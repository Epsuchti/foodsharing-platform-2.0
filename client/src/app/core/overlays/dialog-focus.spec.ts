import { Dialog } from '@openng/optimus-ui/dialog';
import { DomHandler } from '@openng/optimus-ui/dom';

import { keepFocusTakenWhileDialogsOpen } from './dialog-focus';

/** A dialog's delayed opening focus never pulls the user out of what they already started in the dialog. */
describe('dialog opening focus', () => {
  const original = Dialog.prototype._focus;
  let container: HTMLElement;
  let first: HTMLInputElement;
  let second: HTMLInputElement;
  let trigger: HTMLButtonElement;
  let dialog: Dialog;

  beforeEach(() => {
    vi.useFakeTimers();
    keepFocusTakenWhileDialogsOpen();
    document.body.innerHTML = `<button id="trigger">Open</button><div id="dialog"><input id="a" /><input id="b" /></div>`;
    container = document.getElementById('dialog')!;
    first = document.getElementById('a') as HTMLInputElement;
    second = document.getElementById('b') as HTMLInputElement;
    trigger = document.getElementById('trigger') as HTMLButtonElement;
    vi.spyOn(DomHandler, 'getFocusableElements').mockReturnValue([first, second] as never);
    dialog = Object.assign(Object.create(Dialog.prototype) as Dialog, {
      container: () => container,
      transitionOptions: '150ms cubic-bezier(0, 0, 0.2, 1)',
    });
  });

  afterEach(() => {
    Dialog.prototype._focus = original;
    vi.useRealTimers();
    vi.restoreAllMocks();
    document.body.innerHTML = '';
  });

  it('focuses the first field after the delay when the focus is still on the trigger', () => {
    trigger.focus();
    expect(dialog._focus(container)).toBe(true);
    expect(document.activeElement).toBe(trigger);
    vi.advanceTimersByTime(150);
    expect(document.activeElement).toBe(first);
  });

  it('leaves the focus in a field the user went to while the dialog opened', () => {
    trigger.focus();
    dialog._focus(container);
    second.focus();
    vi.advanceTimersByTime(150);
    expect(document.activeElement).toBe(second);
  });

  it('leaves the focus inside the dialog when the user was there before the dialog finished opening', () => {
    second.focus();
    dialog._focus(container);
    vi.advanceTimersByTime(150);
    expect(document.activeElement).toBe(second);
  });

  it('reports false when there is nothing to focus', () => {
    vi.spyOn(DomHandler, 'getFocusableElements').mockReturnValue([] as never);
    expect(dialog._focus(container)).toBe(false);
    expect(dialog._focus(undefined)).toBe(false);
  });
});
