import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { ReportSummary, ReportsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ReportItemComponent } from './report-item.component';

/**
 * Reports block of a profile (G1, `listUserReports`): the reports about the member that the viewer handles. The
 * profile shows it only when `PublicProfile.violationCount` is present (report handlers and ORGA, decision 97).
 */
@Component({
  selector: 'fs-user-reports',
  imports: [...DATA_STATE_IMPORTS, ReportItemComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card block" data-testid="reports.user">
      <h2>
        <i class="pi pi-flag" aria-hidden="true"></i>
        {{ 'reports.user.title' | t: { count: count() } }}
      </h2>
      <fs-data-state
        [state]="reports"
        skeleton="list"
        [compact]="true"
        emptyIcon="pi pi-flag"
        [emptyTitle]="'reports.user.empty' | t"
        data-testid="reports.user.state"
      >
        <ng-template [fsData]="reports" let-list>
          <ul class="list" data-testid="reports.user.list">
            @for (report of list; track report.id) {
              <li><fs-report-item [report]="report" [showReported]="false" /></li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    :host { display: block; }
    .block { display: grid; gap: 0.75rem; }
    h2 { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    h2 .pi { color: var(--fs-danger); }
    .list { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .list fs-report-item { box-shadow: none; border: 1px solid var(--fs-border); }
  `,
})
export class UserReportsComponent {
  private readonly api = inject(ReportsService);

  readonly userId = input.required<string>();
  /** `PublicProfile.violationCount` (the heading count). */
  readonly count = input(0);

  protected readonly reports = loadResource<ReportSummary[], string>({
    params: () => this.userId(),
    loader: (userId) => this.api.listUserReports({ userId }),
  });
}
