import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Role } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';
import { AvatarSize, UserAvatarComponent } from './user-avatar.component';

/** Avatar + name (+ role label or subtitle); a link when `link` is set. */
@Component({
  selector: 'fs-user-chip',
  imports: [NgTemplateOutlet, RouterLink, UserAvatarComponent, TPipe],
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
      />
      <span class="chip__text">
        <span class="chip__name">{{ name() }}</span>
        @if (subtitle()) {
          <span class="chip__sub">{{ subtitle() }}</span>
        } @else if (showRole() && role()) {
          <span class="chip__sub">{{ 'common.role.' + role() | t }}</span>
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
      overflow: hidden;
      font-weight: 650;
      text-overflow: ellipsis;
      white-space: nowrap;
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
}
