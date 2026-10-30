import { Banana, BuddyRelation, PublicProfile } from '../../api';

/** Upstream `BananaMessage` minimum (server rule `ProfileRules.BANANA_MIN_LENGTH`, counted on the stripped text). */
export const BANANA_MIN_LENGTH = 100;
/** `BananaCreate.message` maxLength. */
export const BANANA_MAX_LENGTH = 5000;

export type ProfileTab = 'wall' | 'bananas' | 'commitments' | 'notes' | 'history';

/**
 * Tabs of a profile for the viewer (A6, A46): wall and bananas for everyone signed in, commitments for
 * `maySeeCommitments` (self, admins, managers of the user's stores), admin notes for `maySeeNotes`, the change
 * history for `maySeeHistory`.
 */
export function profileTabs(profile: PublicProfile): ProfileTab[] {
  const p = profile.permissions;
  const tabs: ProfileTab[] = ['wall', 'bananas'];
  if (p.maySeeCommitments) tabs.push('commitments');
  if (p.maySeeNotes) tabs.push('notes');
  if (p.maySeeHistory) tabs.push('history');
  return tabs;
}

/** The requested tab when the viewer may see it, else the wall. */
export function activeTab(profile: PublicProfile, requested: string | null | undefined): ProfileTab {
  const tabs = profileTabs(profile);
  return tabs.includes(requested as ProfileTab) ? (requested as ProfileTab) : 'wall';
}

/** Photo URL in another variant size (A41: `/api/uploads/<id>?size=md` → `?size=lg`); other URLs stay unchanged. */
export function photoInSize(url: string | null | undefined, size: 'sm' | 'md' | 'lg'): string | undefined {
  if (!url) return undefined;
  if (!url.startsWith('/api/uploads/')) return url;
  const [path, query = ''] = url.split('?');
  const params = new URLSearchParams(query);
  params.set('size', size);
  return `${path}?${params.toString()}`;
}

/** What the buddy button offers for a relation (A16). */
export type BuddyAction = 'request' | 'withdraw' | 'accept' | 'remove';

/** Primary (and optional secondary) buddy action of the viewer for a relation; none on the own profile. */
export function buddyActions(relation: BuddyRelation, self: boolean): BuddyAction[] {
  if (self) return [];
  switch (relation) {
    case BuddyRelation.None:
      return ['request'];
    case BuddyRelation.RequestedByMe:
      return ['withdraw'];
    case BuddyRelation.RequestedByThem:
      return ['accept', 'remove'];
    case BuddyRelation.Confirmed:
      return ['remove'];
  }
}

/** Recipient, sender or ORGA may delete a banana (server rule `ProfileRules.mayDeleteBanana`). */
export function mayDeleteBanana(banana: Banana, viewerId: string | undefined, orga: boolean): boolean {
  return orga || (!!viewerId && (banana.recipient.id === viewerId || banana.sender.id === viewerId));
}

/**
 * Icon class of an achievement (`ProfileAchievement.icon` uses Lucide-style names). Names PrimeIcons knows map
 * directly; the others to the closest PrimeIcon; unknown ones fall back to a trophy.
 */
const ACHIEVEMENT_ICONS: Readonly<Record<string, string>> = {
  book: 'book',
  briefcase: 'briefcase',
  'cooking-pot': 'shopping-bag',
  cpu: 'microchip',
  handshake: 'users',
  package: 'box',
  'party-popper': 'gift',
  shield: 'shield',
  sparkles: 'sparkles',
  sprout: 'sun',
  sun: 'sun',
  truck: 'truck',
  wrench: 'wrench',
  trophy: 'trophy',
  star: 'star',
  heart: 'heart',
};

/**
 * PrimeIcons offered by the achievement icon picker (plan-m7 Q9); the picker stores the bare name (`trophy`), which
 * {@link achievementIcon} renders as `pi pi-trophy`.
 */
export const ACHIEVEMENT_ICON_CHOICES: readonly string[] = [
  'trophy', 'star', 'heart', 'crown', 'verified', 'shield', 'sparkles', 'sun', 'gift', 'thumbs-up', 'face-smile',
  'users', 'book', 'graduation-cap', 'lightbulb', 'briefcase', 'wrench', 'hammer', 'truck', 'car', 'box',
  'shopping-bag', 'megaphone', 'calendar', 'camera', 'palette', 'globe', 'flag', 'bolt', 'microchip', 'wallet',
];
const KNOWN_ICONS: Readonly<Record<string, string>> = {
  ...Object.fromEntries(ACHIEVEMENT_ICON_CHOICES.map((name) => [name, name])),
  ...ACHIEVEMENT_ICONS,
};

export function achievementIcon(icon: string | null | undefined): string {
  const name = icon ? KNOWN_ICONS[icon.replace(/^pi[- ]+(pi-)?/, '')] : undefined;
  return `pi pi-${name ?? 'trophy'}`;
}
