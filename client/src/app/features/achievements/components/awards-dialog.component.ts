import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { Achievement, AchievementsService, Award } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { DEFAULT_TIME_ZONE } from '../../../core/dates/time-zone';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { NOTICE_MAX, awardExpired, isoDay, sortAwards, toAwardUpdate } from '../achievements.util';

/**
 * Awards of one achievement (AC1, administrators): active ones first, then revoked. Each active award can get a new
 * end date or notice (`updateAward`) or be revoked (`revokeAward`, soft). Emits `changed` so the grid reloads its
 * counts.
 */
@Component({
  selector: 'fs-awards-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Dialog, InputText, ReactiveFormsModule, TPipe, Textarea, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="'achievements.awards.title' | t: { name: achievement()?.name ?? '' }"
      [closeAriaLabel]="'common.close' | t"
      [style]="{ width: 'min(42rem, 94vw)' }"
      data-testid="achievements.awards"
    >
      <fs-data-state
        [state]="awards"
        skeleton="list"
        [emptyTitle]="'achievements.awards.empty' | t"
        [emptyText]="'achievements.awards.emptyText' | t"
        emptyIcon="pi pi-trophy"
        [compact]="true"
        testId="achievements.awards.state"
        data-testid="achievements.awards.state"
      >
        <ng-template [fsData]="awards" let-list>
          <ul class="list" data-testid="achievements.awards.list">
            @for (award of sorted(list); track award.id) {
              <li
                class="award"
                [class.award--revoked]="!!award.revokedAt"
                data-testid="achievements.awards.item"
                [attr.data-award-id]="award.id"
                [attr.data-user-id]="award.user.id"
                [attr.data-status]="award.revokedAt ? 'revoked' : expired(award) ? 'expired' : 'active'"
              >
                <div class="award__head">
                  <fs-user-chip [name]="award.user.displayName" [userId]="award.user.id" [avatarUrl]="award.user.avatarUrl" [link]="['/profile', award.user.id]" />
                  @if (award.revokedAt) {
                    <span class="tag tag--revoked" data-testid="achievements.awards.revoked">{{ 'achievements.awards.revoked' | t: { date: (award.revokedAt | fsDate: 'date') } }}</span>
                  } @else if (expired(award)) {
                    <span class="tag" data-testid="achievements.awards.expired">{{ 'achievements.awards.expired' | t }}</span>
                  }
                </div>
                <p class="meta">
                  <span>{{ 'achievements.awards.awardedAt' | t: { date: (award.awardedAt | fsDate: 'date') } }}</span>
                  <span>·</span>
                  <span data-testid="achievements.awards.by" [attr.data-automatic]="!award.awardedBy">
                    @if (award.awardedBy; as by) {
                      {{ 'achievements.awards.by' | t: { name: by.displayName } }}
                    } @else {
                      {{ 'achievements.awards.automatic' | t }}
                    }
                  </span>
                  <span>·</span>
                  <span data-testid="achievements.awards.validUntil" [attr.data-valid-until]="award.validUntil ?? ''">
                    {{ award.validUntil ? ('achievements.awards.until' | t: { date: (award.validUntil | fsDate: 'date') }) : ('achievements.awards.unlimited' | t) }}
                  </span>
                </p>
                @if (award.notice) {
                  <p class="notice" data-testid="achievements.awards.notice">{{ award.notice }}</p>
                }
                @if (editing() === award.id) {
                  <form class="edit" [formGroup]="editForm" (ngSubmit)="update.run(award)">
                    <div class="field">
                      <label [for]="'award-edit-until-' + award.id">{{ 'achievements.award.validUntil' | t }}</label>
                      <input pInputText type="date" [id]="'award-edit-until-' + award.id" formControlName="validUntil" data-testid="achievements.awards.editValidUntil" />
                    </div>
                    <div class="field">
                      <label [for]="'award-edit-notice-' + award.id">{{ 'achievements.award.notice' | t }}</label>
                      <textarea pTextarea rows="2" [id]="'award-edit-notice-' + award.id" formControlName="notice" [maxlength]="noticeMax" data-testid="achievements.awards.editNotice"></textarea>
                    </div>
                    <div class="edit__actions">
                      <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" size="small" (onClick)="editing.set(null)" />
                      <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" size="small" [loading]="update.busy()" [disabled]="editForm.invalid" data-testid="achievements.awards.editSave" />
                    </div>
                  </form>
                } @else if (!award.revokedAt) {
                  <div class="actions">
                    <p-button
                      [label]="'achievements.awards.edit' | t"
                      icon="pi pi-pencil"
                      size="small"
                      severity="secondary"
                      [text]="true"
                      [ariaLabel]="('achievements.awards.edit' | t) + ': ' + award.user.displayName"
                      (onClick)="startEdit(award)"
                      data-testid="achievements.awards.edit"
                    />
                    <p-button
                      [label]="'achievements.awards.revoke' | t"
                      icon="pi pi-ban"
                      size="small"
                      severity="danger"
                      [text]="true"
                      [ariaLabel]="('achievements.awards.revoke' | t) + ': ' + award.user.displayName"
                      (onClick)="askRevoke(award)"
                      data-testid="achievements.awards.revoke"
                    />
                  </div>
                }
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </p-dialog>
  `,
  styles: `
    .list { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .award { display: grid; gap: 0.35rem; padding: 0.75rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); }
    .award--revoked { border-style: dashed; background: var(--fs-surface-muted); }
    .award__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.35rem; margin: 0; color: var(--fs-text-muted); font-size: 0.84rem; }
    .notice { margin: 0; font-size: 0.9rem; white-space: pre-line; }
    .tag { padding: 0.1rem 0.5rem; border: 1px solid var(--fs-border); border-radius: 999px; color: var(--fs-text-muted); font-size: 0.78rem; font-weight: 600; }
    .tag--revoked { border-color: var(--fs-danger-border); color: var(--fs-danger); }
    .actions { display: flex; flex-wrap: wrap; gap: 0.25rem; }
    .edit { display: grid; gap: 0.6rem; padding-top: 0.25rem; }
    .field { display: grid; gap: 0.3rem; }
    .field label { font-weight: 650; font-size: 0.85rem; }
    .field input { max-width: 14rem; }
    .field textarea { width: 100%; }
    .edit__actions { display: flex; justify-content: flex-end; gap: 0.4rem; }
  `,
})
export class AwardsDialogComponent {
  private readonly api = inject(AchievementsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly visible = model(false);
  readonly achievement = input<Achievement | null>(null);
  readonly changed = output<void>();

  protected readonly noticeMax = NOTICE_MAX;
  protected readonly editing = signal<string | null>(null);
  protected readonly editForm = new FormGroup({
    validUntil: new FormControl('', { nonNullable: true }),
    notice: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(NOTICE_MAX)] }),
  });
  private readonly today = isoDay(new Date(), DEFAULT_TIME_ZONE);

  private readonly achievementId = computed(() => (this.visible() ? this.achievement()?.id : undefined));
  protected readonly awards = loadResource<Award[], string>({
    params: () => this.achievementId(),
    loader: (achievementId) => this.api.listAwards({ achievementId }),
  });

  protected readonly update = runMutation(
    (award: Award) => {
      const { validUntil, notice } = this.editForm.getRawValue();
      return this.api.updateAward({
        awardId: award.id,
        awardUpdate: toAwardUpdate({ validUntil, notice }, award.validUntil),
      });
    },
    {
      form: this.editForm,
      successToastKey: 'achievements.awards.updated',
      onSuccess: () => {
        this.editing.set(null);
        this.awards.reload();
      },
    },
  );

  protected readonly revoke = runMutation((award: Award) => this.api.revokeAward({ awardId: award.id }), {
    successToastKey: 'achievements.awards.revokedToast',
    onSuccess: () => {
      this.awards.reload();
      this.changed.emit();
    },
  });

  protected sorted(list: Award[]): Award[] {
    return sortAwards(list);
  }

  protected expired(award: Award): boolean {
    return awardExpired(award, this.today);
  }

  protected startEdit(award: Award): void {
    this.editForm.reset({ validUntil: award.validUntil ?? '', notice: award.notice ?? '' });
    this.editing.set(award.id);
  }

  protected askRevoke(award: Award): void {
    this.confirm.confirm({
      header: this.i18n.t('achievements.awards.revokeTitle', { name: award.user.displayName }),
      message: this.i18n.t('achievements.awards.revokeText'),
      acceptLabel: this.i18n.t('achievements.awards.revoke'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.revoke.run(award),
    });
  }
}
