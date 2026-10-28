import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { IntroPickupAdminService, PastImportPreview, PastImportResult } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { CsvDropzoneComponent, CsvFile } from '../../../shared/csv-dropzone/csv-dropzone.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ProgressJobComponent } from '../../../shared/progress-job/progress-job.component';

type RowFilter = 'all' | 'CREATE' | 'SKIP_DUPLICATE' | 'NEEDS_ATTENTION';

/**
 * `/regions/:regionId/intro-pickups/import` (EA19, EA20; CAN_MANAGE_USERS): CSV of past intro pickups (reference
 * table format) → preview with status and reasons per row, store/category mapping summaries → confirm → result;
 * large files run as a job with a progress bar. Importing twice creates nothing new.
 */
@Component({
  selector: 'fs-past-import-page',
  imports: [Button, CsvDropzoneComponent, PageHeaderComponent, ProgressJobComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.import.title' | t" [subtitle]="'introPickups.import.subtitle' | t" icon="pi pi-upload" [backLink]="['/regions', regionId(), 'intro-pickups', 'admin']" [backLabel]="'introPickups.admin.title' | t" />
    <section class="fs-card card">
      <h2 class="card__title"><i class="pi pi-file" aria-hidden="true"></i>{{ 'introPickups.import.step1' | t }}</h2>
      <p class="muted">{{ 'introPickups.import.format' | t }}</p>
      <fs-csv-dropzone testId="introPickups.import.csv" (fileChange)="onFile($event)" />
      <div class="actions">
        <p-button [label]="'introPickups.import.preview' | t" icon="pi pi-eye" [disabled]="!file()" [loading]="preview.busy()" (onClick)="preview.run()" data-testid="introPickups.import.previewButton" />
      </div>
      @if (preview.error(); as error) {
        <p class="error-text" role="alert" data-testid="introPickups.import.error">{{ 'errors.' + error.code | t }}</p>
      }
    </section>

    @if (result(); as p) {
      <section class="fs-card card" data-testid="introPickups.import.preview">
        <h2 class="card__title"><i class="pi pi-list-check" aria-hidden="true"></i>{{ 'introPickups.import.step2' | t }}</h2>
        <ul class="counts">
          <li><strong data-testid="introPickups.import.total">{{ p.counts.totalRows }}</strong>{{ 'introPickups.import.rows' | t }}</li>
          <li class="ok"><strong data-testid="introPickups.import.willCreate">{{ p.counts.willCreate }}</strong>{{ 'introPickups.import.willCreate' | t }}</li>
          <li><strong data-testid="introPickups.import.willSkip">{{ p.counts.willSkip }}</strong>{{ 'introPickups.import.willSkip' | t }}</li>
          <li class="warn"><strong data-testid="introPickups.import.attention">{{ p.counts.needsAttention }}</strong>{{ 'introPickups.import.needsAttention' | t }}</li>
          <li><strong>{{ p.counts.knownUsers }} / {{ p.counts.knownUsers + p.counts.unknownUsers }}</strong>{{ 'introPickups.import.knownUsers' | t }}</li>
        </ul>
        @if (p.storeMappings.length || p.categoryMappings.length) {
          <div class="mappings">
            @for (m of p.storeMappings; track m.from) {
              <span class="tag" data-testid="introPickups.import.storeMapping">{{ m.from }} → {{ m.to }} ({{ m.count }})</span>
            }
            @for (m of p.categoryMappings; track m.from) {
              <span class="tag" data-testid="introPickups.import.categoryMapping">{{ m.from }} → {{ 'common.introCategory.' + m.to | t }} ({{ m.count }})</span>
            }
          </div>
        }
        <div class="switch" role="group" [attr.aria-label]="'introPickups.import.filter' | t">
          @for (f of filters; track f) {
            <p-button [label]="'introPickups.import.filter.' + f | t" size="small" [outlined]="filter() !== f" (onClick)="filter.set(f)" [attr.data-testid]="'introPickups.import.filter.' + f" />
          }
        </div>
        <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'introPickups.scrollTable' | t">
          <table class="table">
            <thead>
              <tr>
                <th class="num">{{ 'introPickups.import.col.line' | t }}</th>
                <th>{{ 'introPickups.import.col.person' | t }}</th>
                <th>{{ 'introPickups.import.col.date' | t }}</th>
                <th>{{ 'introPickups.import.col.store' | t }}</th>
                <th>{{ 'introPickups.form.category' | t }}</th>
                <th>{{ 'introPickups.import.col.status' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (row of rows(); track row.line) {
                <tr [attr.data-status]="row.status" data-testid="introPickups.import.row">
                  <td class="num">{{ row.line }}</td>
                  <td>{{ row.userDisplayName ?? row.foodsaverNumber ?? '–' }}</td>
                  <td>{{ row.date ?? row.rawDate }}</td>
                  <td>{{ row.store || row.rawStore }}</td>
                  <td>{{ row.category ? ('common.introCategory.' + row.category | t) : row.rawCategory }}@if (row.visitFairteiler) { · FT }</td>
                  <td>
                    <span class="tags">
                      <span [class]="'tag ' + statusClass(row.status)" data-testid="introPickups.import.status">{{ 'introPickups.import.status.' + row.status | t }}</span>
                      @for (reason of row.reasons; track reason) {
                        <span class="tag" [attr.data-reason]="reason" data-testid="introPickups.import.reason">{{ 'introPickups.import.reason.' + reason | t }}</span>
                      }
                    </span>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="actions">
          <p-button [label]="'introPickups.import.confirm' | t: { count: p.counts.willCreate }" icon="pi pi-check" [disabled]="!p.counts.willCreate || !!done()" [loading]="confirmImport.busy()" (onClick)="confirmImport.run()" data-testid="introPickups.import.confirm" />
        </div>
      </section>
    }

    @if (done(); as d) {
      <section class="fs-card card" data-testid="introPickups.import.result">
        <h2 class="card__title"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'introPickups.import.step3' | t }}</h2>
        @if (d.jobId) {
          <fs-progress-job [jobId]="d.jobId" [label]="'introPickups.import.running' | t" [doneLabel]="'introPickups.import.jobDone' | t" testId="introPickups.import.job" />
        } @else {
          <p data-testid="introPickups.import.resultText">{{ 'introPickups.import.resultText' | t: { created: d.created, skipped: d.skipped, attention: d.needsAttention } }}</p>
        }
      </section>
    }
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .counts {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .counts li {
      display: grid;
      min-width: 7rem;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .counts strong {
      color: var(--fs-text);
      font-size: 1.3rem;
    }
    .counts .ok {
      border-color: var(--fs-primary);
    }
    .counts .warn {
      border-color: var(--fs-warning-border);
      background: var(--fs-warning-soft);
    }
    .mappings,
    .switch {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
  `,
})
export class PastImportPageComponent {
  private readonly api = inject(IntroPickupAdminService);
  readonly regionId = input.required<string>();

  protected readonly filters: readonly RowFilter[] = ['all', 'CREATE', 'SKIP_DUPLICATE', 'NEEDS_ATTENTION'];
  protected readonly filter = signal<RowFilter>('all');
  protected readonly file = signal<CsvFile | null>(null);
  protected readonly result = signal<PastImportPreview | null>(null);
  protected readonly done = signal<PastImportResult | null>(null);
  protected readonly rows = computed(() => {
    const filter = this.filter();
    const rows = this.result()?.rows ?? [];
    return filter === 'all' ? rows : rows.filter((row) => row.status === filter);
  });

  protected onFile(file: CsvFile): void {
    this.file.set(file);
    this.result.set(null);
    this.done.set(null);
  }

  protected statusClass(status: string): string {
    return status === 'CREATE' ? 'tag--ok' : status === 'NEEDS_ATTENTION' ? 'tag--warn' : '';
  }

  protected readonly preview = runMutation(
    () => this.api.previewPastImport({ regionId: this.regionId(), pastImportRequest: { csv: this.file()!.text } }),
    {
      errorToast: false,
      onSuccess: (preview) => {
        this.filter.set('all');
        this.result.set(preview);
      },
    },
  );

  protected readonly confirmImport = runMutation(
    () => this.api.confirmPastImport({ regionId: this.regionId(), pastImportRequest: { csv: this.file()!.text } }),
    { successToastKey: 'introPickups.import.imported', onSuccess: (result) => this.done.set(result) },
  );
}
