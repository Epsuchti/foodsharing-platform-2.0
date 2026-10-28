import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { QuizStatus } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PHASE_TONE, quizPhase } from '../quiz-kind';

/** Status chip of a quiz for the current user: not started / running / passed (confirm pending) / failed, wait / … */
@Component({
  selector: 'fs-quiz-status-chip',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-phase]': 'phase()', '[class]': '"chip chip--" + tone()' },
  template: `
    <i [class]="icon()" aria-hidden="true"></i>
    <span>{{ 'quiz.phase.' + phase() | t: params() }}</span>
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      width: fit-content;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.8rem;
      font-weight: 650;
      line-height: 1.4;
      white-space: nowrap;
    }
    .pi {
      font-size: 0.8rem;
    }
    :host(.chip--neutral) {
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
    :host(.chip--info) {
      background: color-mix(in srgb, #2563eb 14%, transparent);
      color: color-mix(in srgb, #2563eb 80%, var(--fs-text));
    }
    :host(.chip--success) {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    :host(.chip--warning) {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    :host(.chip--danger) {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
  `,
})
export class QuizStatusChipComponent {
  readonly status = input.required<QuizStatus>();

  protected readonly phase = computed(() => quizPhase(this.status()));
  protected readonly tone = computed(() => PHASE_TONE[this.phase()]);
  protected readonly params = computed(() => {
    const status = this.status();
    return {
      days: Math.abs(status.currentWaitDays ?? 0) || (status.expiresInDays ?? 0),
      answered: status.running?.questionsAnswered ?? 0,
      total: status.running?.questionCount ?? 0,
    };
  });
  protected readonly icon = computed(
    () =>
      ({
        notStarted: 'pi pi-circle',
        running: 'pi pi-play',
        confirmPending: 'pi pi-exclamation-circle',
        passed: 'pi pi-check-circle',
        expiring: 'pi pi-hourglass',
        failed: 'pi pi-times-circle',
        wait: 'pi pi-clock',
        disqualified: 'pi pi-ban',
      })[this.phase()],
  );
}
