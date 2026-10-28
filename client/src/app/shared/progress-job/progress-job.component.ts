import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { Subscription, switchMap, takeWhile, timer } from 'rxjs';

import { JobProgress, JobStatus, JobsService } from '../../api';
import { ApiErrorView, toApiErrorView } from '../../core/data/api-error';
import { TPipe } from '../../core/i18n/t.pipe';
import { ErrorStateComponent } from '../data-state/error-state.component';

/**
 * Progress of a long-running server job (conventions A17: batch passport PDFs, CSV imports): polls `getJob` until
 * DONE or FAILED and shows a progress bar. Emits `finished` with the final job (the page then downloads the result
 * or reloads its data). `<fs-progress-job [jobId]="job.jobId" (finished)="onDone($event)" />`
 */
@Component({
  selector: 'fs-progress-job',
  imports: [ErrorStateComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-progress-job',
    '[attr.data-status]': 'status()',
    '[attr.data-testid]': 'testId()',
  },
  template: `
    @if (error(); as failure) {
      <fs-error-state [error]="failure" [compact]="true" [retryable]="true" (retry)="start()" />
    } @else {
      <div class="head">
        <span class="title">
          @switch (status()) {
            @case ('DONE') {
              <i class="pi pi-check-circle done" aria-hidden="true"></i>
              {{ doneLabel() ?? ('shared.job.done' | t) }}
            }
            @case ('FAILED') {
              <i class="pi pi-times-circle failed" aria-hidden="true"></i>
              {{ 'shared.job.failed' | t }}
            }
            @default {
              <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
              {{ label() ?? ('shared.job.running' | t) }}
            }
          }
        </span>
        @if (current(); as state) {
          <span class="count">{{ 'shared.job.count' | t: { done: state.done, total: state.total } }}</span>
        }
      </div>
      <div
        class="bar"
        role="progressbar"
        aria-valuemin="0"
        aria-valuemax="100"
        [attr.aria-valuenow]="percent()"
        [attr.aria-label]="label() ?? ('shared.job.running' | t)"
      >
        <span class="bar__fill" [class.bar__fill--failed]="status() === 'FAILED'" [style.width.%]="percent()"></span>
      </div>
      @if (current()?.message; as message) {
        <p class="message">{{ message }}</p>
      }
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.5rem;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 0.5rem;
      font-size: 0.92rem;
    }
    .title {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      font-weight: 650;
    }
    .done {
      color: var(--fs-primary);
    }
    .failed {
      color: var(--fs-danger);
    }
    .count {
      color: var(--fs-text-muted);
      font-variant-numeric: tabular-nums;
    }
    .bar {
      height: 0.6rem;
      overflow: hidden;
      border-radius: 999px;
      background: var(--fs-surface-muted);
    }
    .bar__fill {
      display: block;
      height: 100%;
      border-radius: 999px;
      background: var(--fs-primary);
      transition: width 300ms ease;
    }
    .bar__fill--failed {
      background: var(--fs-danger);
    }
    .message {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class ProgressJobComponent {
  private readonly jobsApi = inject(JobsService);

  /** Job to poll. */
  readonly jobId = input<string | undefined>(undefined);
  /** A fixed job state instead of polling (gallery, tests). */
  readonly job = input<JobProgress | undefined>(undefined);
  readonly label = input<string | undefined>(undefined);
  readonly doneLabel = input<string | undefined>(undefined);
  /** Poll interval in ms. */
  readonly interval = input(1000);
  readonly testId = input('shared.job');
  readonly finished = output<JobProgress>();

  private readonly polled = signal<JobProgress | null>(null);
  protected readonly current = computed(() => this.job() ?? this.polled());
  protected readonly error = signal<ApiErrorView | null>(null);
  protected readonly status = computed<JobStatus | 'PENDING'>(() => this.current()?.status ?? 'PENDING');
  protected readonly percent = computed(() => {
    const job = this.current();
    if (!job) {
      return 0;
    }
    if (job.status === JobStatus.Done) {
      return 100;
    }
    return job.total > 0 ? Math.min(100, Math.round((job.done / job.total) * 100)) : 0;
  });

  private subscription: Subscription | null = null;

  constructor() {
    effect(() => {
      const id = this.jobId();
      if (id && !this.job()) {
        untracked(() => this.start());
      }
    });
    inject(DestroyRef).onDestroy(() => this.subscription?.unsubscribe());
  }

  /** (Re)starts polling (also the retry of a failed poll). */
  start(): void {
    this.subscription?.unsubscribe();
    this.error.set(null);
    const id = this.jobId();
    if (!id) {
      return;
    }
    this.subscription = timer(0, this.interval())
      .pipe(
        switchMap(() => this.jobsApi.getJob({ jobId: id })),
        takeWhile((job) => job.status === JobStatus.Running, true),
      )
      .subscribe({
        next: (job) => {
          this.polled.set(job);
          if (job.status !== JobStatus.Running) {
            this.finished.emit(job);
          }
        },
        error: (thrown: unknown) => this.error.set(toApiErrorView(thrown)),
      });
  }
}
