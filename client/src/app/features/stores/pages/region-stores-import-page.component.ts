import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';
import { map } from 'rxjs';

import { ApiErrorCode, RegionDetail, RegionsService, StoreAdminService, StoreImportRow, StoresImportPreview } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { ApiErrorView } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { CsvDropzoneComponent, CsvFile } from '../../../shared/csv-dropzone/csv-dropzone.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { canConfirmStoresImport, mayImportStores } from '../stores.util';

export const STORES_IMPORT_COLUMNS =
  'name,category,chain,street,postcode,city,latitude,longitude,cooperation_status,team_status,public_info,manager_foodsaver_numbers';

const FORBIDDEN: ApiErrorView = {
  status: 403,
  code: ApiErrorCode.AccessDenied,
  message: 'Only the store coordination of the region may import stores',
  retryable: false,
  fieldErrors: [],
};

/**
 * /regions/:regionId/stores/import (S2, D16.2): the store coordination of a region uploads or pastes a stores CSV,
 * sees a dry-run preview per row (status, errors, warnings, counts) and confirms the import. Users who may not
 * coordinate the region's stores get the forbidden state (the server re-checks).
 */
@Component({
  selector: 'fs-region-stores-import-page',
  imports: [...DATA_STATE_IMPORTS, Button, CsvDropzoneComponent, FormsModule, PageHeaderComponent, RouterLink, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'stores.import.title' | t"
      [subtitle]="region.data()?.name ?? ''"
      icon="pi pi-file-import"
      [backLink]="listLink()"
      [backLabel]="'stores.import.backToList' | t"
    />

    <fs-data-state
      [state]="region"
      skeleton="form"
      [backLink]="listLink()"
      [askWho]="'stores.import.askWho' | t"
      testId="stores.import.state"
      data-testid="stores.import.state"
    >
      <ng-template [fsData]="region">
        @if (result(); as done) {
          <section class="fs-card card result" role="status" data-testid="stores.import.result">
            <h2><i class="pi pi-check-circle" aria-hidden="true"></i> {{ 'stores.import.doneTitle' | t }}</h2>
            <p>{{ 'stores.import.summary' | t: summaryParams(done) }}</p>
            <div class="actions">
              <p-button
                [label]="'stores.import.backToList' | t"
                icon="pi pi-list"
                [routerLink]="listLink()"
                data-testid="stores.import.backToList"
              />
              <p-button
                [label]="'stores.import.again' | t"
                icon="pi pi-refresh"
                severity="secondary"
                [outlined]="true"
                (onClick)="reset()"
                data-testid="stores.import.again"
              />
            </div>
          </section>
        } @else {
          <section class="fs-card card input">
            <p class="muted">{{ 'stores.import.help' | t }}</p>
            <code class="columns" data-testid="stores.import.columns">{{ columns }}</code>
            <ul class="hints muted">
              <li>{{ 'stores.import.hintHeader' | t }}</li>
              <li>{{ 'stores.import.hintManagers' | t }}</li>
              <li>{{ 'stores.import.hintMatch' | t }}</li>
            </ul>
            <fs-csv-dropzone (fileChange)="onFile($event)" testId="stores.import.csv" />
            <label for="stores-import-text" class="label">{{ 'stores.import.paste' | t }}</label>
            <textarea
              pTextarea
              id="stores-import-text"
              rows="6"
              class="text"
              spellcheck="false"
              [ngModel]="text()"
              (ngModelChange)="text.set($event)"
              data-testid="stores.import.text"
            ></textarea>
            <div class="actions">
              <p-button
                [label]="'stores.import.preview' | t"
                icon="pi pi-eye"
                severity="secondary"
                [disabled]="!text().trim()"
                [loading]="preview.busy() && pending() === 'dry'"
                (onClick)="runPreview()"
                data-testid="stores.import.previewButton"
              />
            </div>
          </section>

          @if (previewResult(); as result) {
            <section class="fs-card card" data-testid="stores.import.preview">
              <h2>{{ 'stores.import.previewTitle' | t }}</h2>
              <p class="summary" data-testid="stores.import.summary">{{ 'stores.import.summary' | t: summaryParams(result) }}</p>
              @if (stale()) {
                <p class="warning" role="status" data-testid="stores.import.stale">
                  <i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'stores.import.stale' | t }}
                </p>
              }
              <div class="table-wrap">
                <table class="table" data-testid="stores.import.table">
                  <caption class="sr-only">{{ 'stores.import.previewTitle' | t }}</caption>
                  <thead>
                    <tr>
                      <th scope="col">{{ 'stores.import.colLine' | t }}</th>
                      <th scope="col">{{ 'stores.import.colStore' | t }}</th>
                      <th scope="col">{{ 'stores.import.colAddress' | t }}</th>
                      <th scope="col">{{ 'stores.import.colCategory' | t }}</th>
                      <th scope="col">{{ 'stores.import.colStatus' | t }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (row of result.rows; track row.line) {
                      <tr
                        [class.row--error]="row.status === 'ERROR'"
                        data-testid="stores.import.row"
                        [attr.data-status]="row.status"
                        [attr.data-line]="row.line"
                      >
                        <td class="number">{{ row.line }}</td>
                        <td class="name">{{ row.name }}</td>
                        <td class="muted">{{ address(row) }}</td>
                        <td>{{ row.category }}@if (row.chain) { · {{ row.chain }}}</td>
                        <td>
                          <span class="status" [attr.data-status]="row.status">{{ 'stores.import.status.' + row.status | t }}</span>
                          @for (error of row.errors; track error) {
                            <span class="message message--error" data-testid="stores.import.rowError">{{ 'stores.import.error.' + error | t }}</span>
                          }
                          @for (warning of row.warnings; track warning) {
                            <span class="message message--warning" data-testid="stores.import.rowWarning">{{ 'stores.import.warning.' + warning | t }}</span>
                          }
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <div class="actions">
                <p-button
                  [label]="'stores.import.confirm' | t: { count: result.created + result.updated }"
                  icon="pi pi-check"
                  [disabled]="!mayConfirm()"
                  [loading]="preview.busy() && pending() === 'import'"
                  (onClick)="runImport()"
                  data-testid="stores.import.confirm"
                />
              </div>
            </section>
          }
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../stores.scss',
  styles: `
    :host { display: block; width: min(100%, 64rem); margin: 0 auto; }
    .card { display: grid; gap: 0.75rem; margin-bottom: 1rem; }
    .card h2 { margin: 0; font-size: 1.1rem; display: flex; align-items: center; gap: 0.5rem; }
    .result h2 i { color: var(--fs-success, var(--fs-primary)); }
    .columns { display: block; padding: 0.5rem 0.75rem; border-radius: var(--fs-radius-md, 0.5rem); background: var(--fs-surface-muted); overflow-wrap: anywhere; font-size: 0.85rem; }
    .hints { margin: 0; padding-left: 1.25rem; font-size: 0.9rem; }
    .label { font-weight: 650; }
    .text { width: 100%; font-family: var(--fs-font-mono, monospace); font-size: 0.85rem; }
    .actions { display: flex; flex-wrap: wrap; gap: 0.5rem; justify-content: flex-end; }
    .summary { font-weight: 650; margin: 0; }
    .warning { margin: 0; color: var(--fs-text-muted); }
    .table-wrap { overflow-x: auto; }
    .table { width: 100%; border-collapse: collapse; }
    .table th, .table td { padding: 0.5rem 0.6rem; text-align: left; border-bottom: 1px solid var(--fs-border); vertical-align: top; }
    .table th { font-size: 0.82rem; color: var(--fs-text-muted); font-weight: 650; }
    .number { font-variant-numeric: tabular-nums; color: var(--fs-text-muted); }
    .name { font-weight: 650; }
    .row--error { background: var(--fs-danger-soft, var(--fs-surface-muted)); }
    .status { font-weight: 650; }
    .message { display: block; font-size: 0.85rem; }
    .message--error { color: var(--fs-danger); }
    .message--warning { color: var(--fs-text-muted); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
    @media (max-width: 40rem) {
      .table thead { display: none; }
      .table tr { display: grid; padding: 0.4rem 0; border-bottom: 1px solid var(--fs-border); }
      .table td { border: 0; padding: 0.15rem 0.6rem; }
    }
  `,
})
export class RegionStoresImportPageComponent {
  private readonly regionsApi = inject(RegionsService);
  private readonly api = inject(StoreAdminService);
  private readonly session = inject(SessionService);

  protected readonly columns = STORES_IMPORT_COLUMNS;
  protected readonly regionId = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p.get('regionId') ?? '')), {
    initialValue: '',
  });
  protected readonly listLink = computed(() => ['/regions', this.regionId(), 'stores']);

  /** The region; users who may not coordinate its stores get `forbidden`. */
  protected readonly region = loadResource<RegionDetail, string>({
    params: () => this.regionId() || undefined,
    loader: (regionId) =>
      this.regionsApi.getRegion({ regionId }).pipe(
        map((region) => {
          if (!mayImportStores(this.session.session(), region)) {
            throw FORBIDDEN;
          }
          return region;
        }),
      ),
  });

  protected readonly text = signal('');
  /** The text of the last dry run (confirm imports exactly what was previewed). */
  private readonly previewedText = signal<string | null>(null);
  protected readonly previewResult = signal<StoresImportPreview | null>(null);
  protected readonly result = signal<StoresImportPreview | null>(null);
  protected readonly pending = signal<'dry' | 'import' | null>(null);

  protected readonly stale = computed(() => !!this.previewResult() && this.previewedText() !== this.text());
  protected readonly mayConfirm = computed(() =>
    canConfirmStoresImport(this.previewResult(), this.previewedText(), this.text()),
  );

  protected readonly preview = runMutation(
    (csv: string, dryRun: boolean) =>
      this.api.importStoresCsv({ regionId: this.regionId(), storesCsvImport: { csv, dryRun } }),
    {
      onSuccess: (preview) => {
        this.pending.set(null);
        if (preview.dryRun) {
          this.previewResult.set(preview);
        } else {
          this.result.set(preview);
        }
      },
      onError: () => this.pending.set(null),
    },
  );

  protected onFile(file: CsvFile): void {
    this.text.set(file.text);
    this.runPreview();
  }

  protected runPreview(): void {
    const csv = this.text();
    if (!csv.trim()) {
      return;
    }
    this.pending.set('dry');
    this.previewedText.set(csv);
    this.previewResult.set(null);
    void this.preview.run(csv, true);
  }

  protected runImport(): void {
    if (!this.mayConfirm()) {
      return;
    }
    this.pending.set('import');
    void this.preview.run(this.previewedText()!, false);
  }

  protected reset(): void {
    this.text.set('');
    this.previewedText.set(null);
    this.previewResult.set(null);
    this.result.set(null);
  }

  protected address(row: StoreImportRow): string {
    return [row.street, [row.postcode, row.city].filter(Boolean).join(' ')].filter(Boolean).join(', ');
  }

  protected summaryParams(preview: StoresImportPreview): Record<string, number> {
    return { created: preview.created, updated: preview.updated, skipped: preview.skipped, errors: preview.errors };
  }
}
