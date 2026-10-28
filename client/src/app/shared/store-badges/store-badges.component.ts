import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { CooperationStatus, FreeSlotStatus, TeamSearchStatus, UserTeamStatus } from '../../api';
import { TPipe } from '../../core/i18n/t.pipe';

export type StoreChipTone = 'neutral' | 'success' | 'info' | 'warn' | 'danger';

const CHIP_STYLES = `
  :host {
    display: inline-flex;
    align-items: center;
    gap: 0.35rem;
    width: fit-content;
    padding: 0.15rem 0.6rem;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 650;
    line-height: 1.4;
    white-space: nowrap;
  }
  .pi { font-size: 0.75rem; }
  :host(.chip--neutral) { background: var(--fs-surface-muted); color: var(--fs-text-muted); }
  :host(.chip--success) { background: color-mix(in srgb, #25834a 16%, var(--fs-surface)); color: var(--fs-text); }
  :host(.chip--info) { background: color-mix(in srgb, #2563eb 16%, var(--fs-surface)); color: var(--fs-text); }
  :host(.chip--warn) { background: color-mix(in srgb, #d97706 20%, var(--fs-surface)); color: var(--fs-text); }
  :host(.chip--danger) { background: color-mix(in srgb, #dc2626 16%, var(--fs-surface)); color: var(--fs-text); }
`;

const COOPERATION: Record<CooperationStatus, { tone: StoreChipTone; icon: string }> = {
  UNCLEAR: { tone: 'neutral', icon: 'pi pi-question-circle' },
  NO_CONTACT: { tone: 'neutral', icon: 'pi pi-circle' },
  IN_NEGOTIATION: { tone: 'info', icon: 'pi pi-comments' },
  DOES_NOT_WANT_TO_WORK_WITH_US: { tone: 'danger', icon: 'pi pi-ban' },
  COOPERATION_ESTABLISHED: { tone: 'success', icon: 'pi pi-check-circle' },
  GIVES_TO_OTHER_CHARITY: { tone: 'warn', icon: 'pi pi-heart' },
  PERMANENTLY_CLOSED: { tone: 'danger', icon: 'pi pi-lock' },
};

/** Cooperation status of a store (S1): established / in negotiation / … with tone and icon. */
@Component({
  selector: 'fs-cooperation-status-badge',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"chip chip--" + meta().tone', '[attr.data-status]': 'status()' },
  template: `<i [class]="meta().icon" aria-hidden="true"></i><span>{{ 'shared.store.cooperation.' + status() | t }}</span>`,
  styles: CHIP_STYLES,
})
export class CooperationStatusBadgeComponent {
  readonly status = input.required<CooperationStatus>();
  protected readonly meta = computed(() => COOPERATION[this.status()] ?? COOPERATION.UNCLEAR);
}

const TEAM: Record<TeamSearchStatus, { tone: StoreChipTone; icon: string }> = {
  CLOSED: { tone: 'neutral', icon: 'pi pi-lock' },
  OPEN: { tone: 'info', icon: 'pi pi-users' },
  OPEN_SEARCHING: { tone: 'warn', icon: 'pi pi-megaphone' },
};

/** Team search status of a store (S5): closed / open / urgently looking for members. */
@Component({
  selector: 'fs-team-status-badge',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"chip chip--" + meta().tone', '[attr.data-status]': 'status()' },
  template: `<i [class]="meta().icon" aria-hidden="true"></i><span>{{ 'shared.store.team.' + status() | t }}</span>`,
  styles: CHIP_STYLES,
})
export class TeamStatusBadgeComponent {
  readonly status = input.required<TeamSearchStatus>();
  protected readonly meta = computed(() => TEAM[this.status()] ?? TEAM.CLOSED);
}

const MEMBERSHIP: Record<UserTeamStatus, { tone: StoreChipTone; icon: string }> = {
  NO_MEMBER: { tone: 'neutral', icon: 'pi pi-user' },
  APPLIED: { tone: 'info', icon: 'pi pi-send' },
  INVITED: { tone: 'info', icon: 'pi pi-envelope' },
  WAITING_LIST: { tone: 'warn', icon: 'pi pi-clock' },
  MEMBER: { tone: 'success', icon: 'pi pi-user' },
  COORDINATOR: { tone: 'success', icon: 'pi pi-star' },
};

/** The current user's status in a store team (A19 UserTeamStatus). */
@Component({
  selector: 'fs-membership-badge',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"chip chip--" + meta().tone', '[attr.data-status]': 'status()' },
  template: `<i [class]="meta().icon" aria-hidden="true"></i><span>{{ 'shared.store.membership.' + status() | t }}</span>`,
  styles: CHIP_STYLES,
})
export class MembershipBadgeComponent {
  readonly status = input.required<UserTeamStatus>();
  protected readonly meta = computed(() => MEMBERSHIP[this.status()] ?? MEMBERSHIP.NO_MEMBER);
}

const DOT_COLORS: Record<FreeSlotStatus, string> = {
  GREEN: '#25834a',
  YELLOW: '#ca8a04',
  ORANGE: '#ea580c',
  RED: '#dc2626',
};

/**
 * Free-slot traffic light of a store (upstream "free slots in the next days"): GREEN = all covered … RED = many free
 * slots soon. Colour plus a text label (tooltip + accessible name), never colour alone.
 */
@Component({
  selector: 'fs-free-slot-dot',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-status]': 'status()' },
  template: `
    <span
      class="dot"
      role="img"
      [style.background]="color()"
      [attr.title]="'shared.store.freeSlots.' + status() | t: { count: count() }"
      [attr.aria-label]="'shared.store.freeSlots.' + status() | t: { count: count() }"
    ></span>
    @if (showLabel()) {
      <span class="label">{{ 'shared.store.freeSlots.' + status() | t: { count: count() } }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.8rem; color: var(--fs-text-muted); }
    .dot { display: inline-block; width: 0.7rem; height: 0.7rem; border-radius: 50%; flex: none;
      box-shadow: 0 0 0 2px var(--fs-surface); }
  `,
})
export class FreeSlotDotComponent {
  readonly status = input.required<FreeSlotStatus>();
  readonly count = input(0);
  readonly showLabel = input(false);
  protected readonly color = computed(() => DOT_COLORS[this.status()] ?? DOT_COLORS.GREEN);
}

export const STORE_BADGES = [
  CooperationStatusBadgeComponent,
  TeamStatusBadgeComponent,
  MembershipBadgeComponent,
  FreeSlotDotComponent,
] as const;
