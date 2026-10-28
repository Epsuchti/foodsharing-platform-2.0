import { ChangeDetectionStrategy, Component } from '@angular/core';
import { of } from 'rxjs';

import { JobProgress, JobStatus, Role } from '../../../../api';
import { CalendarExportButtonComponent } from '../../../../shared/calendar-export-button/calendar-export-button.component';
import { FoodsaverIdHelpComponent } from '../../../../shared/foodsaver-id-help/foodsaver-id-help.component';
import { PhoneButtonComponent } from '../../../../shared/phone/phone-button.component';
import { ProgressJobComponent } from '../../../../shared/progress-job/progress-job.component';
import { UserChipComponent } from '../../../../shared/user-chip/user-chip.component';

const ICS = [
  'BEGIN:VCALENDAR',
  'VERSION:2.0',
  'PRODID:-//foodsharing//gallery//DE',
  'BEGIN:VEVENT',
  'UID:gallery@foodsharing',
  'DTSTAMP:20261102T090000Z',
  'DTSTART:20261104T163000Z',
  'DTEND:20261104T173000Z',
  'SUMMARY:Einführungsabholung',
  'END:VEVENT',
  'END:VCALENDAR',
].join('\r\n');

const job = (status: JobStatus, done: number, total: number, message?: string): JobProgress => ({
  id: 'gallery',
  status,
  done,
  total,
  message,
  resultAvailable: status === JobStatus.Done,
});

/** People and actions: verified chip with foodsaver ID, ID help, phone and calendar buttons, job progress. */
@Component({
  selector: 'fs-gallery-people-section',
  imports: [
    CalendarExportButtonComponent,
    FoodsaverIdHelpComponent,
    PhoneButtonComponent,
    ProgressJobComponent,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-people">UserChip (M2), Phone, Calendar, Job</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.userChip.m2">
        <header class="specimen__head"><code>verified + foodsaver ID</code></header>
        <div class="stack">
          <div class="row">
            <fs-user-chip name="Sarah Müller" userId="u6" [role]="Role.Foodsaver" [verified]="true" [foodsaverNumber]="100006" />
            <fs-foodsaver-id-help [number]="100006" />
          </div>
          <fs-user-chip name="Lea Brunner" userId="u1" [role]="Role.Foodsharer" [showRole]="true" [foodsaverNumber]="100001" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.actions.phoneCalendar">
        <header class="specimen__head"><code>phone + calendar</code></header>
        <div class="stack">
          <fs-phone-button phone="+41791234567" label="Sarah Müller" testId="gallery.phoneButton" />
          <fs-calendar-export-button [ics]="ics" fileName="gallery.ics" feedUrl="https://foodsharing.example/api/public/intro-pickups/ical/demo.ics" testId="gallery.calendar" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.progressJob.states">
        <header class="specimen__head"><code>job running / done / failed / unknown</code></header>
        <div class="stack">
          <fs-progress-job [job]="running" testId="gallery.jobRunning" />
          <fs-progress-job [job]="done" testId="gallery.jobDone" />
          <fs-progress-job [job]="failed" testId="gallery.jobFailed" />
          <fs-progress-job jobId="00000000-0000-4000-8000-00000000dead" testId="gallery.jobUnknown" />
        </div>
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class PeopleGallerySection {
  protected readonly Role = Role;
  protected readonly ics = () => of(ICS);
  protected readonly running = job(JobStatus.Running, 14, 40, 'Ausweise für Zürich Stadt');
  protected readonly done = job(JobStatus.Done, 40, 40);
  protected readonly failed = job(JobStatus.Failed, 12, 40, 'Kein Foto für 3 Personen');
}
