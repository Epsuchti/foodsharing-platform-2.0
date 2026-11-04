/**
 * Icon names from the server (Lucide style: `NotificationType` bell icons, achievement icons) mapped to the icon font
 * of `@openng/icons` (`pi pi-*`, decisions 95/111, 159). The font lacks about half of the Lucide names; those map to
 * the closest icon here. Names the font has pass through; anything else gets the caller's fallback, so no row ever
 * shows an empty icon. `lucide-icons.spec.ts` checks every name against the font's stylesheet.
 */
export const LUCIDE_TO_PI: Readonly<Record<string, string>> = {
  // bell icons (server NotificationType)
  mail: 'envelope',
  'calendar-check': 'calendar-clock',
  'calendar-x': 'calendar-times',
  store: 'shop',
  'badge-check': 'verified',
  'alarm-clock': 'clock',
  bot: 'microchip-ai',
  newspaper: 'file',
  'plug-zap': 'bolt',
  'message-square': 'comment',
  'at-sign': 'at',
  'eye-off': 'eye-slash',
  vote: 'chart-bar',
  'shopping-basket': 'shopping-cart',
  'map-pin': 'map-marker',
  'book-open': 'book',
  'clipboard-list': 'list-check',
  scale: 'sliders-h',
  award: 'trophy',
  'life-buoy': 'question-circle',
  // achievement icons (seed and upstream data)
  'cooking-pot': 'shopping-bag',
  cpu: 'microchip',
  handshake: 'users',
  package: 'box',
  'party-popper': 'gift',
  sprout: 'sun',
};

/** Font icons the app hands out by name: the achievement picker (plan-m7 Q9), stored as the bare name. */
export const PI_ICON_CHOICES: readonly string[] = [
  'trophy', 'star', 'heart', 'crown', 'verified', 'shield', 'sparkles', 'sun', 'gift', 'thumbs-up', 'face-smile',
  'users', 'book', 'graduation-cap', 'lightbulb', 'briefcase', 'wrench', 'hammer', 'truck', 'car', 'box',
  'shopping-bag', 'megaphone', 'calendar', 'camera', 'palette', 'globe', 'flag', 'bolt', 'microchip', 'wallet',
];

/** Server icon names that the font has under the same name (bell types and their explicit icons). */
const PASS_THROUGH: readonly string[] = [
  'bell', 'calendar', 'calendar-plus', 'compass', 'flag', 'graduation-cap', 'heart', 'id-card', 'send', 'shield',
  'trash', 'unlock', 'user-minus', 'user-plus', 'users',
];

const KNOWN: ReadonlySet<string> = new Set([...PASS_THROUGH, ...PI_ICON_CHOICES, ...Object.values(LUCIDE_TO_PI)]);

/** Font icon name (without `pi-`) for a server icon name; `fallback` for empty or unknown names. */
export function piIconName(name: string | null | undefined, fallback: string): string {
  const bare = name?.trim().replace(/^pi[- ]+(pi-)?/, '');
  if (!bare) return fallback;
  const mapped = LUCIDE_TO_PI[bare] ?? bare;
  return KNOWN.has(mapped) ? mapped : fallback;
}

/** `pi pi-<name>` class for a server icon name; `fallback` (e.g. `bell`, `trophy`) for empty or unknown names. */
export function piIcon(name: string | null | undefined, fallback: string): string {
  return `pi pi-${piIconName(name, fallback)}`;
}
