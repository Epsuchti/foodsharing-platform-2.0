import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { switchMap } from 'rxjs';

import { UploadsService, UserProfile, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { DataState } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ImageCropComponent } from '../../../shared/image-crop/image-crop.component';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

/** Edge length of the uploaded square; the server renders the sm/md/lg variants from it (A41). */
export const PHOTO_SIZE = 800;

/** The cropped JPEG as a named file (the multipart part needs a file name). */
export function photoFile(blob: Blob): File {
  return new File([blob], 'profile-photo.jpg', { type: 'image/jpeg' });
}

/**
 * Profile photo (A5, A41): the current picture, "Change photo" opens `fs-image-crop` in a dialog; the square JPEG
 * (≤ 1 MB) is uploaded (`createUpload`) and set as photo (`setMyPhoto`), "Remove" deletes it after a confirmation.
 * The header avatar follows through `SessionService.patchUser`.
 */
@Component({
  selector: 'fs-photo-card',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, ImageCropComponent, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="photo-title" data-testid="account.settings.photo">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-camera"></i></span>
        <div class="card__head-text">
          <h2 id="photo-title">{{ 'account.photo.title' | t }}</h2>
          <p>{{ 'account.photo.text' | t }}</p>
        </div>
      </header>
      <fs-data-state [state]="profile()" skeleton="card" [skeletonRows]="1" data-testid="account.settings.photo.state">
        <ng-template [fsData]="profile()" let-user>
          <div class="photo">
            <fs-user-avatar
              [name]="user.displayName"
              [avatarUrl]="user.avatarUrl"
              [role]="user.role"
              [colorSeed]="user.id"
              size="xl"
              data-testid="account.settings.photo.avatar"
              [attr.data-has-photo]="user.avatarUrl ? 'true' : 'false'"
            />
            <div class="photo__actions">
              <p-button
                [label]="(user.avatarUrl ? 'account.photo.change' : 'account.photo.add') | t"
                icon="pi pi-upload"
                size="small"
                (onClick)="dialogOpen.set(true)"
                data-testid="account.settings.photo.change"
              />
              @if (user.avatarUrl) {
                <p-button
                  [label]="'account.photo.remove' | t"
                  icon="pi pi-trash"
                  size="small"
                  severity="danger"
                  [text]="true"
                  [loading]="remove.busy()"
                  (onClick)="confirmRemove()"
                  data-testid="account.settings.photo.remove"
                />
              }
              <p class="photo__hint">{{ 'account.photo.hint' | t }}</p>
            </div>
          </div>
        </ng-template>
      </fs-data-state>
    </section>

    <p-dialog [closeAriaLabel]="'common.close' | t"
      [visible]="dialogOpen()"
      (visibleChange)="!$event && close()"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [header]="'account.photo.dialogTitle' | t"
      [style]="{ width: 'min(26rem, calc(100vw - 2rem))' }"
      data-testid="account.settings.photo.dialog"
    >
      @if (dialogOpen()) {
        <fs-image-crop [outputSize]="photoSize" testId="account.settings.photo.crop" (cropped)="upload.run($event)" />
        @if (upload.busy()) {
          <p class="uploading" role="status" data-testid="account.settings.photo.uploading">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'account.photo.uploading' | t }}
          </p>
        }
      }
    </p-dialog>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .photo {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 1rem 1.5rem;
    }
    .photo__actions {
      display: flex;
      flex: 1 1 14rem;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .photo__hint {
      flex-basis: 100%;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .uploading {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 0.4rem;
      margin: 0.75rem 0 0;
      color: var(--fs-text-muted);
    }
  `,
})
export class PhotoCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly uploadsApi = inject(UploadsService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly profile = input.required<DataState<UserProfile>>();

  protected readonly photoSize = PHOTO_SIZE;
  protected readonly dialogOpen = signal(false);

  protected readonly upload = runMutation(
    (blob: Blob) =>
      this.uploadsApi
        .createUpload({ file: photoFile(blob) })
        .pipe(switchMap((upload) => this.usersApi.setMyPhoto({ photoUpdate: { uploadId: upload.id } }))),
    {
      successToastKey: 'account.photo.saved',
      onSuccess: (result) => {
        this.dialogOpen.set(false);
        this.session.patchUser({ avatarUrl: result.photoUrl });
        this.profile().reload();
      },
    },
  );

  protected readonly remove = runMutation(() => this.usersApi.deleteMyPhoto(), {
    successToastKey: 'account.photo.removed',
    onSuccess: () => {
      this.session.patchUser({ avatarUrl: undefined });
      this.profile().reload();
    },
  });

  protected close(): void {
    if (!this.upload.busy()) {
      this.dialogOpen.set(false);
    }
  }

  protected confirmRemove(): void {
    this.confirm.confirm({
      header: this.i18n.t('account.photo.removeTitle'),
      message: this.i18n.t('account.photo.removeText'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('account.photo.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(),
    });
  }
}
