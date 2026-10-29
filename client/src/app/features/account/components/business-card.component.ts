import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';

import { Role, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { readBlobErrors } from '../../../core/data/api-error';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { businessCardFileName, businessCardRegions, businessCardRoles, saveBlob } from '../account.util';

type CardRole = 'FOODSAVER' | 'STORE_MANAGER' | 'AMBASSADOR';

/**
 * Business card (A14, A49): verified foodsavers download an 85 × 55 mm PDF with name, role, "foodsharing <region>",
 * contact address and mobile number. The role is at most the own role; the region one of the own regions (for
 * AMBASSADOR one the user leads). The values come from the session, so the card needs no own request until the
 * download.
 */
@Component({
  selector: 'fs-business-card',
  imports: [Button, FormsModule, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" aria-labelledby="business-card-title" data-testid="account.settings.businessCard">
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-id-card"></i></span>
        <div class="card__head-text">
          <h2 id="business-card-title">{{ 'account.businessCard.title' | t }}</h2>
          <p>{{ 'account.businessCard.text' | t }}</p>
        </div>
      </header>

      @if (!allowed()) {
        <p class="hint" data-testid="account.settings.businessCard.notAllowed">
          <i class="pi pi-lock" aria-hidden="true"></i>
          <span>{{ 'account.businessCard.notAllowed' | t }}</span>
        </p>
      } @else {
        <form class="form" (submit)="$event.preventDefault(); download.run()" novalidate>
          <div class="field">
            <label for="bc-role">{{ 'account.businessCard.role' | t }}</label>
            <p-select
              inputId="bc-role"
              name="role"
              [options]="roleOptions()"
              optionLabel="label"
              optionValue="value"
              [ngModel]="role()"
              (ngModelChange)="role.set($event)"
              appendTo="body"
              data-testid="account.settings.businessCard.role"
            />
          </div>
          <div class="field">
            <label for="bc-region">{{ 'account.businessCard.region' | t }}</label>
            <p-select
              inputId="bc-region"
              name="region"
              [options]="regions()"
              optionLabel="name"
              optionValue="id"
              [ngModel]="regionId()"
              (ngModelChange)="regionId.set($event)"
              [emptyMessage]="'account.businessCard.noRegion' | t"
              appendTo="body"
              data-testid="account.settings.businessCard.region"
            />
          </div>
          <div class="submit">
            <p-button
              type="submit"
              [label]="'account.businessCard.download' | t"
              icon="pi pi-download"
              [disabled]="!role() || !regionId()"
              [loading]="download.busy()"
              data-testid="account.settings.businessCard.download"
            />
          </div>
        </form>
        @if (download.error(); as error) {
          <p class="problem" role="alert" data-testid="account.settings.businessCard.error" [attr.data-code]="error.code">
            <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
            {{ i18n.error(error.code) }}
          </p>
        }
      }
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .form {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 0.85rem 1rem;
    }
    .field {
      display: grid;
      flex: 1 1 13rem;
      gap: 0.35rem;
      min-width: 0;
    }
    .field label {
      font-weight: 600;
    }
    .field p-select {
      width: 100%;
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
export class BusinessCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(SessionService);
  private readonly document = inject(DOCUMENT);
  protected readonly i18n = inject(I18nService);

  private readonly roles = computed(() => businessCardRoles(this.session.role()));
  protected readonly allowed = computed(() => !!this.session.user()?.verified && this.roles().length > 0);

  protected readonly roleOptions = computed(() => {
    this.i18n.language();
    return this.roles().map((value) => ({ value, label: this.i18n.t(`common.role.${value}`) }));
  });

  /** Defaults to the highest role a card can show. */
  protected readonly role = linkedSignal<Role | null>(() => this.roles().at(-1) ?? null);
  protected readonly regions = computed(() => businessCardRegions(this.session.session().units, this.role()));
  /** Home region first; reset when the role narrows the list. */
  protected readonly regionId = linkedSignal<string | null>(() => this.regions()[0]?.id ?? null);

  /** Errors (BUSINESS_CARD_NOT_ALLOWED, NOT_REGION_MEMBER) are shown below the form. */
  protected readonly download = runMutation(
    () =>
      this.usersApi
        .getBusinessCard({ role: this.role() as CardRole, regionId: this.regionId()! })
        .pipe(readBlobErrors()),
    {
      errorToast: false,
      onSuccess: (pdf) => {
        const slug = this.regions().find((region) => region.id === this.regionId())?.slug;
        saveBlob(this.document, pdf, businessCardFileName(slug));
      },
    },
  );
}
