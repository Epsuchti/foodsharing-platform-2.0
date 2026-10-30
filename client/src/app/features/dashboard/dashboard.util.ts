import { ActivityItem, ActivityType, BasketSummary } from '../../api';

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

/** The excluded types after a chip click: an excluded type comes back, an included one is excluded. */
export function toggleExcluded(excluded: readonly ActivityType[], type: ActivityType): ActivityType[] {
  return excluded.includes(type) ? excluded.filter((entry) => entry !== type) : ACTIVITY_TYPES.filter((entry) => entry === type || excluded.includes(entry));
}

const itemKey = (item: ActivityItem) => `${item.type}|${item.link}|${item.at}|${item.title}`;

/**
 * Appends the next feed page; items already shown are skipped (new activity between two requests shifts the pages,
 * so the start of the next page can repeat the end of the previous one).
 */
export function mergeActivityPages(shown: readonly ActivityItem[], next: readonly ActivityItem[]): ActivityItem[] {
  const seen = new Set(shown.map(itemKey));
  return [...shown, ...next.filter((item) => !seen.has(itemKey(item)))];
}

/** The first baskets of a nearby list (the server sorts by distance). */
export function nearestBaskets(baskets: readonly BasketSummary[], count = NEARBY_BASKETS_SHOWN): BasketSummary[] {
  return baskets.slice(0, count);
}
