import { ChangeDetectionStrategy, Component, effect, input, model, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { Achievement, AwardCreate, UserSummary } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { NOTICE_MAX } from '../achievements.util';

/**
 * Award an achievement (AC1): pick the member (`fs-user-picker`, limited to the achievement's region), an optional end
 * date (default: award day + validity days) and an optional notice. The server refuses self awards and non-members
 * (403 ACHIEVEMENT_RECIPIENT_NOT_MEMBER); OVERRIDE achievements extend an active award instead of adding one.
 */
@Component({
  selector: 'fs-award-dialog',
  imports: [Button, Dialog, InputText, ReactiveFormsModule, TPipe, Textarea, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'achievements.award.title' | t: { name: achievement()?.name ?? '' }"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(32rem, 94vw)' }"
      data-testid="achievements.award"
    >
      <form class="form" [formGroup]="form" (ngSubmit)="submit()">
        @if (recipient(); as user) {
          <div class="recipient" data-testid="achievements.award.recipient" [attr.data-user-id]="user.id">
            <fs-user-chip [name]="user.displayName" [userId]="user.id" [avatarUrl]="user.avatarUrl" />
            <p-button
              icon="pi pi-times"
              size="small"
              severity="secondary"
              [text]="true"
              [rounded]="true"
              [ariaLabel]="'achievements.award.change' | t"
              (onClick)="recipient.set(null)"
              data-testid="achievements.award.change"
            />
          </div>
        } @else {
          <fs-user-picker
            [regionId]="achievement()?.regionId"
            [label]="'achievements.award.recipient' | t"
            (picked)="recipient.set($event)"
            testId="achievements.award.picker"
          />
        }
        <div class="field">
          <label for="award-valid-until">{{ 'achievements.award.validUntil' | t }}</label>
          <input pInputText id="award-valid-until" type="date" formControlName="validUntil" data-testid="achievements.award.validUntil" />
          <small class="hint">
            {{ (achievement()?.validityDays ? 'achievements.award.defaultEnd' : 'achievements.award.noEnd') | t: { days: achievement()?.validityDays ?? 0 } }}
          </small>
        </div>
        <div class="field">
          <label for="award-notice">{{ 'achievements.award.notice' | t }}</label>
          <textarea pTextarea id="award-notice" rows="3" formControlName="notice" [maxlength]="noticeMax" data-testid="achievements.award.notice"></textarea>
        </div>
        @if (achievement()?.duplicateMode === 'OVERRIDE') {
          <small class="hint">{{ 'achievements.award.overrideHint' | t }}</small>
        }
      </form>
      <ng-template #footer>
        <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="visible.set(false)" />
        <p-button
          (onClick)="submit()"
          [label]="'achievements.award.submit' | t"
          icon="pi pi-check"
          [loading]="busy()"
          [disabled]="!recipient() || form.invalid"
          data-testid="achievements.award.submit"
        />
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .form { display: grid; gap: 1rem; }
    .field { display: grid; gap: 0.35rem; }
    .field label { font-weight: 650; font-size: 0.88rem; }
    .field input, .field textarea { width: 100%; }
    .field input { max-width: 14rem; }
    .hint { color: var(--fs-text-muted); font-size: 0.8rem; }
    .recipient {
      display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; padding: 0.5rem 0.75rem;
      border: 1px solid var(--fs-border); border-radius: var(--fs-radius-sm);
    }
  `,
})
export class AwardDialogComponent {
  readonly visible = model(false);
  readonly achievement = input<Achievement | null>(null);
  readonly busy = input(false);
  readonly save = output<AwardCreate>();

  protected readonly noticeMax = NOTICE_MAX;
  protected readonly recipient = signal<UserSummary | null>(null);
  protected readonly form = new FormGroup({
    validUntil: new FormControl('', { nonNullable: true }),
    notice: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(NOTICE_MAX)] }),
  });

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      this.recipient.set(null);
      this.form.reset({ validUntil: '', notice: '' });
    });
  }

  protected submit(): void {
    const recipient = this.recipient();
    if (!recipient || this.form.invalid) return;
    const { validUntil, notice } = this.form.getRawValue();
    this.save.emit({
      userId: recipient.id,
      ...(validUntil ? { validUntil } : {}),
      ...(notice.trim() ? { notice: notice.trim() } : {}),
    });
  }
}
