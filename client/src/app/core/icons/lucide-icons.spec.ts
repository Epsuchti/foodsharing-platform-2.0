import { LUCIDE_TO_PI, PI_ICON_CHOICES, piIcon } from './lucide-icons';

interface Fs {
  readFileSync(path: string, encoding: 'utf8'): string;
}
/** The unit tests run in Node; the icon font's stylesheet lists every icon as `.pi-<name>:before`. */
function fontIconNames(): Set<string> {
  const node = (globalThis as unknown as { process: { getBuiltinModule(id: string): unknown } }).process;
  const fs = node.getBuiltinModule('node:fs') as Fs;
  const css = fs.readFileSync('node_modules/@openng/icons/openng-icons.css', 'utf8');
  return new Set([...css.matchAll(/\.pi-([a-z0-9-]+):before/g)].map((match) => match[1]));
}

/**
 * Every icon string the server sends: the defaults of `NotificationType.java` and the explicit `.icon("…")` calls of
 * the notification requests (server/src/main/java, 2026-11-04).
 */
const SERVER_ICONS = [
  'alarm-clock', 'at-sign', 'award', 'badge-check', 'bell', 'book-open', 'bot', 'calendar', 'calendar-check',
  'calendar-plus', 'calendar-x', 'clipboard-list', 'compass', 'eye-off', 'flag', 'graduation-cap', 'heart', 'id-card',
  'life-buoy', 'mail', 'map-pin', 'message-square', 'newspaper', 'plug-zap', 'scale', 'send', 'shield',
  'shopping-basket', 'store', 'trash', 'unlock', 'user-minus', 'user-plus', 'users', 'vote',
];

describe('lucide icons → icon font (decision 159)', () => {
  const font = fontIconNames();
  const exists = (cls: string) => font.has(cls.replace(/^pi pi-/, ''));

  it('resolves every server icon to an icon the font has', () => {
    // a sentinel fallback: a server icon must resolve by itself, never through the fallback
    expect(SERVER_ICONS.filter((name) => piIcon(name, 'NONE') === 'pi pi-NONE')).toEqual([]);
    expect(SERVER_ICONS.filter((name) => !exists(piIcon(name, 'NONE')))).toEqual([]);
  });

  it('maps only to existing icons and offers only existing picker icons', () => {
    expect(Object.values(LUCIDE_TO_PI).filter((name) => !font.has(name))).toEqual([]);
    expect(PI_ICON_CHOICES.filter((name) => !font.has(name))).toEqual([]);
  });

  it('maps Lucide names, passes font names through and falls back for unknown ones', () => {
    expect(piIcon('mail', 'bell')).toBe('pi pi-envelope');
    expect(piIcon('store', 'bell')).toBe('pi pi-shop');
    expect(piIcon('user-plus', 'bell')).toBe('pi pi-user-plus');
    expect(piIcon('pi-truck', 'trophy')).toBe('pi pi-truck');
    expect(piIcon('pi pi-truck', 'trophy')).toBe('pi pi-truck');
    expect(piIcon('no-such-icon', 'bell')).toBe('pi pi-bell');
    expect(piIcon(undefined, 'trophy')).toBe('pi pi-trophy');
    expect(piIcon('  ', 'bell')).toBe('pi pi-bell');
  });
});
