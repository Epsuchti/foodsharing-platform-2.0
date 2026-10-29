import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { ReportReason, ReportStatus, ReportSummary, Role } from '../../../../api';
import { ReportItemComponent, ReportStatusComponent } from '../../../reports/components/report-item.component';

const JUMPER = { id: 'u7', displayName: 'Jonas Weber', role: Role.Foodsaver, verified: true };
const SARAH = { id: 'u6', displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true };

const OPEN: ReportSummary = {
  id: 'r1',
  regionId: 'zh',
  reason: ReportReason.NoShow,
  reportedUser: JUMPER,
  reporter: SARAH,
  store: { id: 's1', name: 'Bäckerei Kuhn' },
  status: ReportStatus.ToDo,
  createdAt: '2026-10-20T18:00:00Z',
};
const REMINDER: ReportSummary = {
  ...OPEN,
  id: 'r7',
  reason: ReportReason.ViolatedB10,
  store: undefined,
  status: ReportStatus.Reminder,
  reminderAt: '2026-11-03T08:00:00Z',
};
const OTHER: ReportSummary = { ...OPEN, id: 'r6', reason: ReportReason.Other, reasonOther: 'Unfreundlich zur Filialleitung', status: ReportStatus.Completed };

/** Reports (plan-m7 Q8, G1): list items in three states and every status pill. */
@Component({
  selector: 'fs-gallery-reports-section',
  imports: [ReportItemComponent, ReportStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-reports">Reports</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.reportItem.list">
        <header class="specimen__head"><code>fs-report-item (to do with store / reminder / other reason, completed)</code></header>
        <fs-report-item [report]="open" />
        <fs-report-item [report]="reminder" />
        <fs-report-item [report]="other" [showReported]="false" />
      </section>
      <section class="specimen" data-testid="gallery.reportStatus.all">
        <header class="specimen__head"><code>fs-report-status</code></header>
        <div class="pills">
          @for (status of statuses; track status) {
            <fs-report-status [status]="status" />
          }
        </div>
      </section>
    </div>
  `,
  styles: `
    .specimen fs-report-item + fs-report-item { margin-top: 0.75rem; }
    .pills { display: flex; flex-wrap: wrap; gap: 0.5rem; }
  `,
})
export class ReportsGallerySection {
  protected readonly open = OPEN;
  protected readonly reminder = REMINDER;
  protected readonly other = OTHER;
  protected readonly statuses = Object.values(ReportStatus);
}

export const REPORTS_GALLERY_SECTIONS: readonly Type<unknown>[] = [ReportsGallerySection];
