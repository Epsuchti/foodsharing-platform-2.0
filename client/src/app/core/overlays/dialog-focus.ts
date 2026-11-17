import { Dialog } from '@openng/optimus-ui/dialog';
import { DomHandler } from '@openng/optimus-ui/dom';

/**
 * An Optimus dialog moves the focus to its first focusable element once it has opened: after its enter animation
 * (300 ms) and a further delay of its `transitionOptions` (150 ms). Whoever had already started in the dialog by then
 * lost their place: text typed into a field went on in the first field, and an open select list closed when its
 * select lost the focus. The dialog now only gives the focus when it is still outside the dialog and has not moved
 * since the dialog opened; otherwise it stays where the user put it.
 */
export function keepFocusTakenWhileDialogsOpen(): void {
  Dialog.prototype._focus = function (this: Dialog, parent?: HTMLElement): boolean {
    if (!parent) {
      return false;
    }
    const focusable = DomHandler.getFocusableElements(parent) as HTMLElement[];
    if (!focusable.length) {
      return false;
    }
    const document = parent.ownerDocument;
    const before = document.activeElement;
    const container = this.container();
    setTimeout(
      () => {
        const active = document.activeElement;
        const userIsThere = active !== before || (active !== null && !!container?.contains(active));
        if (!userIsThere) {
          focusable[0].focus();
        }
      },
      this.parseDurationToMilliseconds(this.transitionOptions) || 5,
    );
    return true;
  };
}
