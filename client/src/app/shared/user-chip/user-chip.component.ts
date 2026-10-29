import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tooltip } from '@openng/optimus-ui/tooltip';

import { Role } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';
import { AvatarSize, UserAvatarComponent } from './user-avatar.component';

/** Avatar + name (+ role label or subtitle); a link when `link` is set. */
@Component({
  selector: 'fs-user-chip',
  imports: [NgTemplateOutlet, RouterLink, Tooltip, UserAvatarComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-user-chip' },
  template: `
    @if (link()) {
      <a class="chip chip--link" [routerLink]="link()">
        <ng-container [ngTemplateOutlet]="body" />
      </a>
    } @else {
      <span class="chip"><ng-container [ngTemplateOutlet]="body" /></span>
    }
    <ng-template #body>
      <fs-user-avatar
        [name]="name()"
        [avatarUrl]="avatarUrl()"
        [role]="role()"
        [size]="size()"
        [colorSeed]="userId()"
        [sleeping]="sleeping()"
      />
      <span class="chip__text">
        <span class="chip__name">
          <span class="chip__name-text">{{ name() }}</span>
          @if (sleeping()) {
            <i
              class="pi pi-moon chip__sleep"
              role="img"
              tabindex="0"
              [attr.aria-label]="sleepLabel() | t: { message: sleepMessage() ?? '' }"
              [pTooltip]="sleepLabel() | t: { message: sleepMessage() ?? '' }"
              data-testid="shared.userChip.sleep"
            ></i>
          }
          @if (verified()) {
            <i
              class="pi pi-verified chip__verified"
              role="img"
              [attr.aria-label]="'shared.userChip.verified' | t"
              [pTooltip]="'shared.userChip.verified' | t"
            ></i>
          }
        </span>
        @if (subtitle()) {
          <span class="chip__sub">{{ subtitle() }}</span>
        } @else if (showRole() && role()) {
          <span class="chip__sub">{{ 'common.role.' + role() | t }}</span>
        }
        @if (foodsaverNumber()) {
          <span
            class="chip__sub chip__number"
            [pTooltip]="'shared.userChip.foodsaverNumberHelp' | t"
            tabindex="0"
          >
            {{ 'shared.userChip.foodsaverNumber' | t: { number: '' + foodsaverNumber() } }}
          </span>
        }
      </span>
    </ng-template>
  `,
  styles: `
    :host {
      display: inline-flex;
      min-width: 0;
      max-width: 100%;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      min-width: 0;
      gap: 0.6rem;
      color: inherit;
      text-decoration: none;
    }
    .chip--link {
      margin: -0.25rem -0.4rem;
      padding: 0.25rem 0.4rem;
      border-radius: var(--fs-radius-sm);
    }
    .chip--link:hover {
      background: var(--fs-surface-muted);
    }
    .chip__text {
      display: grid;
      min-width: 0;
      line-height: 1.25;
    }
    .chip__name {
      display: flex;
      align-items: center;
      gap: 0.3rem;
      min-width: 0;
      font-weight: 650;
    }
    .chip__name-text {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .chip__verified {
      flex: 0 0 auto;
      color: var(--fs-primary);
      font-size: 0.85em;
    }
    .chip__sleep {
      flex: 0 0 auto;
      color: var(--fs-text-muted);
      font-size: 0.85em;
    }
    .chip__number {
      font-variant-numeric: tabular-nums;
    }
    .chip__sub {
      overflow: hidden;
      color: var(--fs-text-muted);
      font-size: 0.8rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
})
export class UserChipComponent {
  readonly name = input.required<string>();
  readonly userId = input<string | null | undefined>(undefined);
  readonly avatarUrl = input<string | null | undefined>(undefined);
  readonly role = input<Role | null | undefined>(undefined);
  readonly subtitle = input<string | undefined>(undefined);
  readonly showRole = input(false);
  readonly size = input<AvatarSize>('sm');
  readonly link = input<string | unknown[] | undefined>(undefined);
  /** Verified foodsaver (Ve2): a check badge after the name. */
  readonly verified = input(false);
  /** Shown as "ID 100006" with a help tooltip (EA3: newcomers give it to their guide). */
  readonly foodsaverNumber = input<number | null | undefined>(undefined);
  /** Sleep mode (A39, `UserSummary.sleepMode`): moon badge with the sleep message as tooltip, grey avatar ring. */
  readonly sleepMode = input<boolean | null | undefined>(undefined);
  readonly sleepMessage = input<string | null | undefined>(undefined);

  protected readonly sleeping = computed(() => this.sleepMode() === true);
  protected readonly sleepLabel = computed(() =>
    this.sleepMessage()?.trim() ? 'shared.userChip.sleepingWithMessage' : 'shared.userChip.sleeping',
  );
}
