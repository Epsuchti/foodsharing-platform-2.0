import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { JobProgress, JobStatus, UsersService } from '../../../api';
import { errorParams } from '../../../core/data/api-error';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ProgressJobComponent } from '../../../shared/progress-job/progress-job.component';
import { downloadUrl } from '../account.util';

/** Where a finished job's file is served (owner only, conventions A17). */
export function jobResultUrl(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}/result`;
}

/**
 * Data export (A21, GDPR): starts the export job (`exportMyData`), shows its progress (`fs-progress-job`) and offers
 * the zip (profile, settings, history, one file per module, uploads) when it is done. One export per 10 minutes;
 * the server answers 429 otherwise, shown below the button.
 */
@Component({
  selector: 'fs-data-export-card',
  imports: [Button, ProgressJobComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="export-title" data-testid="account.settings.export">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-file-export"></i></span>
        <div class="card__head-text">
          <h2 id="export-title">{{ 'account.export.title' | t }}</h2>
          <p>{{ 'account.export.text' | t }}</p>
        </div>
      </header>

      @if (jobId(); as id) {
        <fs-progress-job
          [jobId]="id"
          [label]="'account.export.running' | t"
          [doneLabel]="'account.export.done' | t"
          (finished)="finished.set($event)"
          testId="account.settings.export.job"
        />
      }

      <div class="actions">
        @if (finished()?.status === 'DONE') {
          <p-button
            [label]="'account.export.download' | t"
            icon="pi pi-download"
            (onClick)="download()"
            data-testid="account.settings.export.download"
          />
        } @else {
          <p-button
            [label]="'account.export.start' | t"
            icon="pi pi-play"
            [severity]="jobId() ? 'secondary' : 'primary'"
            [outlined]="!!jobId()"
            [disabled]="!!jobId() && !finished()"
            [loading]="start.busy()"
            (onClick)="start.run()"
            data-testid="account.settings.export.start"
          />
        }
      </div>
      @if (start.error(); as error) {
        <p class="problem" role="alert" data-testid="account.settings.export.error" [attr.data-code]="error.code">
          <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
          {{ i18n.error(error.code, errorParams(error)) }}
        </p>
      }
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    .problem {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-weight: 600;
    }
  `,
})
export class DataExportCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly document = inject(DOCUMENT);
  protected readonly i18n = inject(I18nService);
  protected readonly errorParams = errorParams;

  protected readonly jobId = signal<string | null>(null);
  protected readonly finished = signal<JobProgress | null>(null);

  /** 429 (one export per 10 minutes) is shown below the button, not as toast. */
  protected readonly start = runMutation(() => this.usersApi.exportMyData(), {
    errorToast: false,
    onSuccess: (job) => {
      this.finished.set(null);
      this.jobId.set(job.jobId);
    },
  });

  protected download(): void {
    const id = this.jobId();
    if (id && this.finished()?.status === JobStatus.Done) {
      downloadUrl(this.document, jobResultUrl(id), 'foodsharing-data.zip');
    }
  }
}
