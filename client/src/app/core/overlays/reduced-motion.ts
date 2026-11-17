import { DEFAULT_MOTION_OPTIONS } from '@openng/optimus-ui-motion';

/**
 * Optimus overlays (dialogs, select lists, menus, toasts) enter and leave through `createMotion`. Its default
 * `safe: true` skips the motion under `prefers-reduced-motion: reduce` and then calls the enter/leave hooks at once,
 * inside the motion directive's `afterRenderEffect`. Every signal those hooks read (a select's options and model, the
 * dialog's container, a toast's visibility) became a dependency of that effect, so each later change ran the enter
 * hooks again: the select list was re-appended and re-aligned and lost its focus to the dialog, and its options were
 * never stable enough to click.
 *
 * With `safe: false` the motion always runs its CSS classes and its hooks run after `animationend`/`transitionend`,
 * outside the effect, as for every other user. Reduced motion still shows no movement: styles.scss shortens every
 * enter/leave animation and transition to 1 ms under `prefers-reduced-motion: reduce` (not 0.01 ms: the motion parses
 * its computed "1e-05s" as 105 s, which kept leaving masks and toasts on the page).
 */
export function runOverlayMotionUnderReducedMotion(): void {
  DEFAULT_MOTION_OPTIONS.safe = false;
}
