import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { SleepStatus, UserProfile, UsersService } from '../../../api';
import { DataState } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { DEFAULT_TIME_ZONE } from '../../../core/dates/time-zone';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  SLEEP_MESSAGE_MAX,
  SleepForm,
  sleepFormChanged,
  sleepFormError,
  sleepFormOf,
  sleepUpdate,
  todayIn,
} from '../account.util';

const STATUSES: readonly { value: SleepStatus; icon: string }[] = [
  { value: SleepStatus.None, icon: 'pi pi-sun' },
  { value: SleepStatus.Temporary, icon: 'pi pi-calendar' },
  { value: SleepStatus.Full, icon: 'pi pi-moon' },
];

/**
 * Sleep mode (A9, A39): NONE / TEMPORARY (from–until) / FULL plus an optional message shown to members who may
 * see contact details. While sleeping, the moon badge appears on the profile, in member lists and on user chips.
 */
@Component({
  selector: 'fs-sleep-mode-card',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, InputText, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="sleep-title" data-testid="account.settings.sleep">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-moon"></i></span>
        <div class="card__head-text">
          <h2 id="sleep-title">{{ 'account.sleep.title' | t }}</h2>
          <p>{{ 'account.sleep.text' | t }}</p>
        </div>
      </header>
      <fs-data-state [state]="profile()" skeleton="form" [skeletonRows]="2" data-testid="account.settings.sleep.state">
        <ng-template [fsData]="profile()" let-user>
          <p class="hint" data-testid="account.settings.sleep.current" [attr.data-status]="user.sleepStatus ?? 'NONE'">
            <i [class]="currentIcon(user)" aria-hidden="true"></i>
            <span>
              @switch (user.sleepStatus) {
                @case ('FULL') {
                  {{ 'account.sleep.currentFull' | t }}
                }
                @case ('TEMPORARY') {
                  {{
                    'account.sleep.currentTemporary'
                      | t: { from: (user.sleepFrom | fsDate: 'date'), until: (user.sleepUntil | fsDate: 'date') }
                  }}
                }
                @default {
                  {{ 'account.sleep.currentNone' | t }}
                }
              }
            </span>
          </p>
          <form class="form" (submit)="$event.preventDefault(); save.run()" novalidate>
            <div class="options" role="radiogroup" aria-labelledby="sleep-status-label">
              <span id="sleep-status-label" class="fs-visually-hidden">{{ 'account.sleep.statusLabel' | t }}</span>
              @for (option of statuses; track option.value) {
                <button
                  type="button"
                  role="radio"
                  class="option"
                  [class.option--active]="form().status === option.value"
                  [attr.aria-checked]="form().status === option.value"
                  (click)="patch({ status: option.value })"
                  [attr.data-testid]="'account.settings.sleep.status.' + option.value"
                >
                  <i [class]="option.icon" aria-hidden="true"></i>
                  <span class="option__text">
                    <span class="option__label">{{ 'account.sleep.status.' + option.value | t }}</span>
                    <span class="option__hint">{{ 'account.sleep.statusHint.' + option.value | t }}</span>
                  </span>
                </button>
              }
            </div>

            @if (form().status === 'TEMPORARY') {
              <div class="dates">
                <div class="field">
                  <label for="sleep-from">{{ 'account.sleep.from' | t }}</label>
                  <input
                    pInputText
                    id="sleep-from"
                    type="date"
                    [ngModel]="form().from"
                    (ngModelChange)="patch({ from: $event ?? '' })"
                    name="from"
                    data-testid="account.settings.sleep.from"
                  />
                </div>
                <div class="field">
                  <label for="sleep-until">{{ 'account.sleep.until' | t }}</label>
                  <input
                    pInputText
                    id="sleep-until"
                    type="date"
                    [min]="form().from || today()"
                    [ngModel]="form().until"
                    (ngModelChange)="patch({ until: $event ?? '' })"
                    name="until"
                    data-testid="account.settings.sleep.until"
                  />
                </div>
              </div>
            }

            @if (form().status !== 'NONE') {
              <div class="field">
                <label for="sleep-message">{{ 'account.sleep.message' | t }}</label>
                <textarea
                  pTextarea
                  id="sleep-message"
                  rows="3"
                  [maxlength]="messageMax"
                  [ngModel]="form().message"
                  (ngModelChange)="patch({ message: $event ?? '' })"
                  name="message"
                  aria-describedby="sleep-message-hint"
                  data-testid="account.settings.sleep.message"
                ></textarea>
                <small id="sleep-message-hint" class="muted">{{ 'account.sleep.messageHint' | t }}</small>
              </div>
            }

            @if (problem(); as key) {
              <p class="problem" role="alert" data-testid="account.settings.sleep.problem">
                <i class="pi pi-exclamation-circle" aria-hidden="true"></i> {{ key | t }}
              </p>
            }

            <div class="actions">
              <p-button
                type="submit"
                [label]="'common.save' | t"
                icon="pi pi-check"
                size="small"
                [disabled]="!changed() || !!problem()"
                [loading]="save.busy()"
                data-testid="account.settings.sleep.save"
              />
            </div>
          </form>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .form {
      display: grid;
      gap: 1rem;
      margin-top: 1rem;
    }
    .options {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 11rem), 1fr));
      gap: 0.5rem;
    }
    .option {
      display: flex;
      align-items: flex-start;
      gap: 0.6rem;
      padding: 0.7rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .option:hover {
      border-color: var(--fs-border-strong);
      background: var(--fs-surface-muted);
    }
    .option > .pi {
      margin-top: 0.2rem;
      color: var(--fs-text-muted);
    }
    .option--active,
    .option--active:hover {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .option--active > .pi {
      color: var(--fs-primary-strong);
    }
    .option__text {
      display: grid;
      gap: 0.1rem;
    }
    .option__label {
      font-weight: 700;
    }
    .option__hint {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .dates {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 12rem), 1fr));
      gap: 0.75rem;
    }
    .field {
      display: grid;
      gap: 0.3rem;
    }
    .field label {
      font-weight: 600;
    }
    .muted {
      color: var(--fs-text-muted);
    }
    .problem {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-weight: 600;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class SleepModeCardComponent {
  private readonly usersApi = inject(UsersService);

  readonly profile = input.required<DataState<UserProfile>>();
  /** "Today" for the date rule (overridable in tests). */
  readonly now = input<Date | undefined>(undefined);

  protected readonly statuses = STATUSES;
  protected readonly messageMax = SLEEP_MESSAGE_MAX;
  protected readonly form = signal<SleepForm>({ status: SleepStatus.None, from: '', until: '', message: '' });
  private readonly stored = computed(() => {
    const user = this.profile().data();
    return user ? sleepFormOf(user) : null;
  });
  protected readonly today = computed(() => todayIn(DEFAULT_TIME_ZONE, this.now() ?? new Date()));
  protected readonly problem = computed(() => sleepFormError(this.form(), this.today()));
  protected readonly changed = computed(() => {
    const stored = this.stored();
    return !!stored && sleepFormChanged(this.form(), stored);
  });

  constructor() {
    effect(() => {
      const stored = this.stored();
      if (stored) {
        untracked(() => this.form.set(stored));
      }
    });
  }

  protected readonly save = runMutation(() => this.usersApi.updateSleepMode({ sleepModeUpdate: sleepUpdate(this.form()) }), {
    successToastKey: 'account.sleep.saved',
    onSuccess: () => this.profile().reload(),
  });

  protected patch(changes: Partial<SleepForm>): void {
    this.form.update((form) => ({ ...form, ...changes }));
  }

  protected currentIcon(user: UserProfile): string {
    return !user.sleepStatus || user.sleepStatus === SleepStatus.None ? 'pi pi-sun' : 'pi pi-moon';
  }
}
