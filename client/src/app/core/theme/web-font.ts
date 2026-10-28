import { readStorage, writeStorage } from '../storage';

export const FONT_STORAGE_KEY = 'fs.font';

/** Runs `task` when the browser is idle (fallback: after a short timeout). */
export function whenIdle(view: Window, task: () => void): void {
  if ('requestIdleCallback' in view) {
    view.requestIdleCallback(task, { timeout: 5000 });
  } else {
    setTimeout(task, 1000);
  }
}

/**
 * Inter is a progressive enhancement (D11.5.3, styles.scss): the first visit renders with the system font and
 * downloads Inter after the first page has rendered (ShellComponent), so it never competes with the start-up
 * requests. The flag makes the inline script in index.html switch to Inter (`.fs-inter`) before the first paint of
 * the next visit. The current page keeps its font: switching now would re-layout visible text.
 */
export function warmWebFont(document: Document): void {
  if (!document.fonts || readStorage(FONT_STORAGE_KEY) === 'inter') {
    return;
  }
  document.fonts
    .load("1em 'Inter Variable'")
    .then((faces) => {
      if (faces.length) {
        writeStorage(FONT_STORAGE_KEY, 'inter');
      }
    })
    .catch(() => undefined);
}
