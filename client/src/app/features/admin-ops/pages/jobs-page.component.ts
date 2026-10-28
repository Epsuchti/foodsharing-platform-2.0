import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { AdminJobsService, JobInfo } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * `/admin/jobs` (D6.3, ORGA): every scheduled job with cron, last run (time, outcome, message, trigger) and lock
 * state; "Run now" runs it immediately and shows the outcome.
 */
@Component({
  selector: 'fs-jobs-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminOps.jobs.title' | t" [subtitle]="'adminOps.jobs.subtitle' | t" icon="pi pi-clock" />
    <fs-data-state [state]="jobs" skeleton="table" [skeletonRows]="8" data-testid="adminOps.jobs.state">
      <ng-template [fsData]="jobs" let-list>
        <div class="table-wrap fs-card">
          <table class="table" data-testid="adminOps.jobs.table">
            <thead>
              <tr>
                <th scope="col">{{ 'adminOps.jobs.name' | t }}</th>
                <th scope="col">{{ 'adminOps.jobs.cron' | t }}</th>
                <th scope="col">{{ 'adminOps.jobs.lastRun' | t }}</th>
                <th scope="col">{{ 'adminOps.jobs.outcome' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminOps.jobs.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (job of list; track job.name) {
                <tr data-testid="adminOps.jobs.row" [attr.data-job]="job.name" [attr.data-outcome]="job.lastRun?.outcome ?? 'NEVER'">
                  <th scope="row"><code>{{ job.name }}</code></th>
                  <td><code class="muted">{{ job.cron ?? '–' }}</code></td>
                  <td>
                    @if (job.lastRun; as run) {
                      {{ run.startedAt | fsDate: 'dateTime' }}
                      <span class="muted">· {{ 'adminOps.jobs.trigger.' + run.trigger | t }}</span>
                    } @else {
                      <span class="muted">{{ 'adminOps.jobs.never' | t }}</span>
                    }
                  </td>
                  <td>
                    @if (job.lastRun; as run) {
                      <span [class]="'badge badge--' + run.outcome" data-testid="adminOps.jobs.outcome">{{ 'adminOps.jobs.outcome.' + run.outcome | t }}</span>
                      @if (run.message) {
                        <span class="muted message">{{ run.message }}</span>
                      }
                    }
                    @if (job.locked) {
                      <span class="badge badge--HALF_OPEN" data-testid="adminOps.jobs.locked">{{ 'adminOps.jobs.locked' | t }}</span>
                    }
                  </td>
                  <td class="num">
                    <p-button
                      [label]="'adminOps.jobs.run' | t"
                      icon="pi pi-play"
                      size="small"
                      [outlined]="true"
                      [loading]="run.busy() && running() === job.name"
                      (onClick)="start(job)"
                      data-testid="adminOps.jobs.run"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-ops.scss',
})
export class JobsPageComponent {
  private readonly api = inject(AdminJobsService);
  protected readonly running = signal<string | null>(null);

  protected readonly jobs = loadResource<JobInfo[]>({ loader: () => this.api.listAdminJobs() });

  protected readonly run = runMutation((name: string) => this.api.runAdminJob({ name }), {
    successToastKey: 'adminOps.jobs.ran',
    onSuccess: () => this.jobs.reload(),
    onError: () => this.jobs.reload(),
  });

  protected start(job: JobInfo): void {
    this.running.set(job.name);
    void this.run.run(job.name);
  }
}
