/** Controls that Chromium and Firefox focus on a mouse click and WebKit (Safari, every iOS browser) does not. */
const CLICK_FOCUSABLE = 'button, [role="button"], [role="tab"], [role="menuitem"], a[href], summary';

/**
 * The control a click should focus, or null when the browser already did, the control is disabled, or a mousedown
 * handler kept the focus where it was on purpose (`preventDefault`, e.g. Quill's toolbar or a combobox list).
 */
export function clickFocusTarget(
  target: EventTarget | null,
  active: Element | null,
  mousedownPrevented: boolean,
): HTMLElement | null {
  if (mousedownPrevented || !(target instanceof Element)) return null;
  const control = target.closest<HTMLElement>(CLICK_FOCUSABLE);
  if (!control || control.matches(':disabled, [aria-disabled="true"]')) return null;
  if (active && control.contains(active)) return null;
  return control;
}

/**
 * Gives a clicked button or link the focus, as Chromium and Firefox do. WebKit leaves it on the nearest focusable
 * ancestor instead (the shell's `<main tabindex="-1">`, or the body), so a menu closed with Escape had no trigger to
 * return the focus to and a toggle lost its place in the tab order. Runs in the capture phase, before the control's
 * own click handlers, so they see the same focus in every browser. A no-op where the browser already focused it.
 */
export function installClickFocus(document: Document): () => void {
  let prevented = false;
  // bubble phase: runs after the control's own mousedown handlers, so defaultPrevented is final
  const onMousedown = (event: MouseEvent) => (prevented = event.defaultPrevented);
  const onClick = (event: MouseEvent) => {
    // detail 0: a click from the keyboard (Enter, Space) or from script, where the focus already is where it belongs
    if (event.detail === 0) return;
    clickFocusTarget(event.target, document.activeElement, prevented)?.focus({ preventScroll: true });
    prevented = false;
  };
  document.addEventListener('mousedown', onMousedown);
  document.addEventListener('click', onClick, true);
  return () => {
    document.removeEventListener('mousedown', onMousedown);
    document.removeEventListener('click', onClick, true);
  };
}
