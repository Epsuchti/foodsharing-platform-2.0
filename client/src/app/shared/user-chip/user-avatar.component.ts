import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { Role } from '../../api';

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

/** Round avatar: image or coloured initials, optional role badge dot. */
@Component({
  selector: 'fs-user-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-user-avatar',
    '[attr.data-size]': 'size()',
    role: 'img',
    '[attr.aria-label]': 'name()',
  },
  template: `
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
  readonly name = input<string | null | undefined>('');
  readonly avatarUrl = input<string | null | undefined>(undefined);
  readonly size = input<AvatarSize>('md');
  readonly role = input<Role | null | undefined>(undefined);
  /** Seed for the colour (defaults to the name); pass the user id for stable colours across renames. */
  readonly colorSeed = input<string | null | undefined>(undefined);

  protected readonly imageFailed = signal(false);
  protected readonly initials = computed(() => initialsOf(this.name()));
  protected readonly color = computed(() => avatarColor(this.colorSeed() ?? this.name()));
  protected readonly badge = computed(() => {
    const role = this.role();
    return role ? (ROLE_BADGE[role] ?? null) : null;
  });
}
