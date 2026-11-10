import {
  ActivityItem,
  ActivityType,
  BasketSummary,
  EventSummary,
  FreeSlotStatus,
  InvitationStatus,
  StoreListItem,
  UserTeamStatus,
} from '../../api';

export const ACTIVITY_PAGE_SIZE = 10;
/** Nearest baskets on the dashboard card (B2) and the radius they are searched in (server default). */
export const NEARBY_BASKETS_SHOWN = 3;
export const NEARBY_BASKETS_KM = 30;

/** Filter chips in a fixed order (the enum order of the contract). */
export const ACTIVITY_TYPES: readonly ActivityType[] = Object.values(ActivityType);

export const ACTIVITY_TYPE_ICON: Record<ActivityType, string> = {
  [ActivityType.Forum]: 'pi pi-comments',
  [ActivityType.StoreWall]: 'pi pi-shopping-bag',
  [ActivityType.Mailbox]: 'pi pi-envelope',
  [ActivityType.EventWall]: 'pi pi-calendar',
  [ActivityType.FspWall]: 'pi pi-box',
  [ActivityType.BuddyWall]: 'pi pi-users',
};

/** What decides which feed types a user can use (STORE_WALL ↔ verified foodsaver, MAILBOX ↔ `session.mailboxes`). */
export interface ActivityAccess {
  /** Verified foodsaver or above: the pickups/stores navigation rule. */
  stores: boolean;
  /** The server's mailbox flag (decision 149). */
  mailboxes: boolean;
}

/**
 * Filter chips of the activity feed the user can use (review 2026-11-10, D3): no store wall chip without stores, no
 * mailbox chip without mailboxes. The enum order stays.
 */
export function activityTypesFor(access: ActivityAccess): ActivityType[] {
  return ACTIVITY_TYPES.filter(
    (type) => (type !== ActivityType.StoreWall || access.stores) && (type !== ActivityType.Mailbox || access.mailboxes),
  );
}

/** The excluded types after a chip click: an excluded type comes back, an included one is excluded. */
export function toggleExcluded(excluded: readonly ActivityType[], type: ActivityType): ActivityType[] {
  return excluded.includes(type) ? excluded.filter((entry) => entry !== type) : ACTIVITY_TYPES.filter((entry) => entry === type || excluded.includes(entry));
}

/**
 * Appends the next feed page; items already shown (same `id`) are skipped (new activity between two requests shifts
 * the pages, so the start of the next page can repeat the end of the previous one). Items with equal time, link and
 * title are different posts and both stay.
 */
export function mergeActivityPages(shown: readonly ActivityItem[], next: readonly ActivityItem[]): ActivityItem[] {
  const seen = new Set(shown.map((item) => item.id));
  return [...shown, ...next.filter((item) => !seen.has(item.id))];
}

/** The first baskets of a nearby list (the server sorts by distance). */
export function nearestBaskets(baskets: readonly BasketSummary[], count = NEARBY_BASKETS_SHOWN): BasketSummary[] {
  return baskets.slice(0, count);
}

/** Rows of the home cards (docs/design/ui-polish-batch-proposal.md §3). */
export const HELP_NEEDED_SHOWN = 3;
export const UPCOMING_EVENTS_SHOWN = 3;
export const YOUR_STORES_SHOWN = 5;

/** A row of "Waiting for you": what to do, how many, where. */
export interface WaitingRow {
  id: 'invitations' | 'unread';
  icon: string;
  count: number;
  link: string;
}

/** What "Waiting for you" counts; the stores only for verified foodsavers (null otherwise). */
export interface WaitingInput {
  stores: readonly StoreListItem[] | null;
  unreadConversations: number;
}

/**
 * The rows of "Waiting for you", in this order and only with a count above 0: store invitations and unread
 * conversations. Only things the user can act on (review 2026-11-10, D4): unconfirmed pickups and open applications
 * wait on the team, so they are not listed.
 */
export function waitingRows(input: WaitingInput): WaitingRow[] {
  const stores = input.stores ?? [];
  const rows: WaitingRow[] = [
    {
      id: 'invitations',
      icon: 'pi pi-envelope',
      count: stores.filter((store) => store.membership === UserTeamStatus.Invited).length,
      link: '/stores/mine',
    },
    { id: 'unread', icon: 'pi pi-comments', count: input.unreadConversations, link: '/messages' },
  ];
  return rows.filter((row) => row.count > 0);
}

/** The next events I attend, may attend or am invited to (not "won't join"), from now on, soonest first. */
export function upcomingEvents(
  events: readonly EventSummary[],
  now: Date,
  count = UPCOMING_EVENTS_SHOWN,
): EventSummary[] {
  return events
    .filter((event) => event.myStatus !== InvitationStatus.WontJoin && new Date(event.startsAt).getTime() >= now.getTime())
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    .slice(0, count);
}

const FREE_SLOT_URGENCY: readonly FreeSlotStatus[] = [
  FreeSlotStatus.Red,
  FreeSlotStatus.Orange,
  FreeSlotStatus.Yellow,
  FreeSlotStatus.Green,
];

/**
 * "Your stores": stores whose team I am in (member or coordinator) with free slots in the next 7 days, most urgent
 * first (RED → GREEN), then by the next pickup; at most five.
 */
export function storesNeedingPeople(stores: readonly StoreListItem[], count = YOUR_STORES_SHOWN): StoreListItem[] {
  return stores
    .filter(
      (store) =>
        (store.membership === UserTeamStatus.Member || store.membership === UserTeamStatus.Coordinator) &&
        store.freeSlotCount > 0,
    )
    .sort(
      (a, b) =>
        FREE_SLOT_URGENCY.indexOf(a.freeSlotStatus) - FREE_SLOT_URGENCY.indexOf(b.freeSlotStatus) ||
        (a.nextPickupAt ?? '9999').localeCompare(b.nextPickupAt ?? '9999') ||
        a.name.localeCompare(b.name),
    )
    .slice(0, count);
}
