import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';

import { ReportPage, ReportStatus, ReportsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RegionPageContext } from '../../regions/region-page.context';
import { ReportItemComponent } from '../components/report-item.component';
import { SETTABLE_STATUSES } from '../reports.util';

const PAGE_SIZE = 20;

/**
 * /regions/:regionId/reports (G1, `listRegionReports`): the reports of the region (and the regions below it) for
 * report handlers, newest first, filtered by status, paged. Everyone else gets the forbidden state
 * (403 REPORTS_NOT_ACCESSIBLE).
 */
@Component({
  selector: 'fs-region-reports-tab',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, Paginator, ReportItemComponent, RouterLink, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <p class="muted">{{ 'reports.region.intro' | t }}</p>
      <div class="head__actions">
        <div class="filter">
          <label for="reports-status">{{ 'reports.region.status' | t }}</label>
          <p-select
            inputId="reports-status"
            [options]="statusOptions()"
            optionLabel="label"
            optionValue="value"
            [ngModel]="status()"
            (ngModelChange)="setStatus($event ?? null)"
            [showClear]="true"
            [placeholder]="'reports.region.allStatuses' | t"
            appendTo="body"
            [style]="{ minWidth: '13rem' }"
            data-testid="reports.region.status"
          >
            <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
          </p-select>
        </div>
        <p-button
          [label]="'reports.region.log' | t"
          icon="pi pi-history"
          severity="secondary"
          [outlined]="true"
          routerLink="../report-log"
          data-testid="reports.region.log"
        />
      </div>
    </div>
    <fs-data-state
      [state]="reports"
      skeleton="list"
      [emptyTitle]="(status() ? 'reports.region.emptyStatus' : 'reports.region.empty') | t"
      emptyIcon="pi pi-flag"
      data-testid="reports.region.state"
    >
      <ng-template [fsData]="reports" let-result>
        <ul class="list" data-testid="reports.region.list">
          @for (report of result.items; track report.id) {
            <li><fs-report-item [report]="report" /></li>
          }
        </ul>
        @if (result.page.totalPages > 1) {
          <p-paginator
            [first]="result.page.page * result.page.size"
            [rows]="result.page.size"
            [totalRecords]="result.page.totalItems"
            [pageLinkSize]="3"
            (onPageChange)="onPage($event)"
            data-testid="reports.region.paginator"
          />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: end; justify-content: space-between; margin-bottom: 1rem; }
    .head__actions { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: end; }
    .filter { display: grid; gap: 0.3rem; }
    .filter label { font-weight: 650; font-size: 0.85rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; max-width: 40rem; }
    .list { display: grid; gap: 0.75rem; margin: 0 0 1rem; padding: 0; list-style: none; }
  `,
})
export class RegionReportsTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(ReportsService);
  private readonly i18n = inject(I18nService);

  protected readonly status = signal<ReportStatus | null>(null);
  private readonly page = signal(0);

  protected readonly statusOptions = computed(() =>
    SETTABLE_STATUSES.map((value) => ({ value, label: this.i18n.t(`reports.status.${value}`) })),
  );

  protected readonly reports = loadResource<ReportPage, { regionId: string; status: ReportStatus | null; page: number }>({
    params: () => {
      const regionId = this.ctx.regionId();
      return regionId ? { regionId, status: this.status(), page: this.page() } : undefined;
    },
    loader: ({ regionId, status, page }) =>
      this.api.listRegionReports({ regionId, status: status ?? undefined, page, size: PAGE_SIZE }),
  });

  protected setStatus(status: ReportStatus | null): void {
    this.status.set(status);
    this.page.set(0);
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
