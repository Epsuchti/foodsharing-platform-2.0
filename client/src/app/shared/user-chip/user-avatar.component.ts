import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Role } from '../../api';
import { SessionService } from '../../core/auth/session.service';

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

/** Warm, readable background colours for initials (white text passes AA on all of them). */
const AVATAR_COLORS = [
  '#25834a',
  '#1c6f91',
  '#8a4fb0',
  '#b5532d',
  '#2f6e6a',
  '#9a3f62',
  '#5a6b1f',
  '#3d5aa8',
];

const ROLE_BADGE: Partial<Record<Role, string>> = {
  [Role.Foodsaver]: 'foodsaver',
  [Role.StoreManager]: 'store-manager',
  [Role.Ambassador]: 'ambassador',
  [Role.Orga]: 'orga',
};

export function initialsOf(name: string | null | undefined): string {
  const parts = (name ?? '')
    .trim()
    .split(/[\s._@-]+/)
    .filter(Boolean);
  if (parts.length === 0) {
    return '?';
  }
  const letters =
    parts.length === 1 ? parts[0].slice(0, 2) : parts[0][0] + parts[parts.length - 1][0];
  return letters.toUpperCase();
}

export function avatarColor(seed: string | null | undefined): string {
  let hash = 0;
  for (const char of seed ?? '') {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

/**
 * Round avatar: image or coloured initials, optional role badge dot. With `link="profile"` and a `userId` it links to
 * the person's profile (when logged in); any other `link` is used as the router link.
 */
@Component({
  selector: 'fs-user-avatar',
  imports: [NgTemplateOutlet, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-user-avatar',
    '[attr.data-size]': 'size()',
    '[attr.data-sleeping]': 'sleeping() ? "" : null',
    '[attr.role]': 'target() ? null : "img"',
    '[attr.aria-label]': 'target() ? null : name()',
  },
  template: `
    @if (target(); as target) {
      <a class="av__link" [routerLink]="target" [attr.aria-label]="name()" data-testid="shared.userAvatar.link">
        <ng-container [ngTemplateOutlet]="face" />
      </a>
    } @else {
      <ng-container [ngTemplateOutlet]="face" />
    }
    <ng-template #face>
    @if (avatarUrl() && !imageFailed()) {
      <img [src]="avatarUrl()" alt="" (error)="imageFailed.set(true)" />
    } @else {
      <span class="av__initials" [style.background]="color()" aria-hidden="true">{{
        initials()
      }}</span>
    }
    @if (badge()) {
      <span class="av__badge" [attr.data-role]="badge()" aria-hidden="true"></span>
    }
    </ng-template>
  `,
  styles: `
    :host {
      --size: 2.5rem;
      position: relative;
      display: inline-grid;
      flex: 0 0 auto;
      width: var(--size);
      height: var(--size);
    }
    :host([data-size='xs']) {
      --size: 1.5rem;
    }
    :host([data-size='sm']) {
      --size: 2rem;
    }
    :host([data-size='lg']) {
      --size: 3.25rem;
    }
    :host([data-size='xl']) {
      --size: 4.5rem;
    }
    .av__link {
      display: block;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      color: inherit;
    }
    img,
    .av__initials {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      object-fit: cover;
    }
    .av__initials {
      display: grid;
      place-items: center;
      color: #fff;
      font-size: calc(var(--size) * 0.38);
      font-weight: 700;
      letter-spacing: 0.02em;
      user-select: none;
    }
    /* sleep mode (A39): grey ring and desaturated picture */
    :host([data-sleeping]) img,
    :host([data-sleeping]) .av__initials {
      box-shadow:
        0 0 0 2px var(--fs-surface),
        0 0 0 4px var(--fs-text-muted);
      filter: grayscale(0.6);
    }
    .av__badge {
      position: absolute;
      right: -1px;
      bottom: -1px;
      width: calc(var(--size) * 0.32);
      height: calc(var(--size) * 0.32);
      min-width: 0.6rem;
      min-height: 0.6rem;
      border: 2px solid var(--fs-surface);
      border-radius: 50%;
      background: var(--fs-primary);
    }
    .av__badge[data-role='store-manager'] {
      background: #1c6f91;
    }
    .av__badge[data-role='ambassador'] {
      background: #d08a12;
    }
    .av__badge[data-role='orga'] {
      background: #9a3f62;
    }
  `,
})
export class UserAvatarComponent {
  private readonly session = inject(SessionService);

  readonly name = input<string | null | undefined>('');
  readonly avatarUrl = input<string | null | undefined>(undefined);
  readonly size = input<AvatarSize>('md');
  readonly role = input<Role | null | undefined>(undefined);
  /** Seed for the colour (defaults to the name); pass the user id for stable colours across renames. */
  readonly colorSeed = input<string | null | undefined>(undefined);
  /** Sleep mode (A39): grey ring. */
  readonly sleeping = input(false);
  /** The person shown; with `link="profile"` the avatar links to `/profile/:userId`. */
  readonly userId = input<string | null | undefined>(undefined);
  /** `'profile'`, another router link, or null (default: no link). */
  readonly link = input<string | unknown[] | null>(null);

  protected readonly target = computed(() => {
    const link = this.link();
    if (link !== 'profile') {
      return link;
    }
    const userId = this.userId();
    return userId && this.session.isAuthenticated() ? ['/profile', userId] : null;
  });

  /** Broken image → initials; a new `avatarUrl` (e.g. a just uploaded photo) tries again. */
  protected readonly imageFailed = linkedSignal({ source: this.avatarUrl, computation: () => false });
  protected readonly initials = computed(() => initialsOf(this.name()));
  protected readonly color = computed(() => avatarColor(this.colorSeed() ?? this.name()));
  protected readonly badge = computed(() => {
    const role = this.role();
    return role ? (ROLE_BADGE[role] ?? null) : null;
  });
}
