import { DEFAULT_MOTION_OPTIONS, createMotion, mergeOptions, shouldSkipMotion } from '@openng/optimus-ui-motion';

import { runOverlayMotionUnderReducedMotion } from './reduced-motion';

/**
 * Under `prefers-reduced-motion: reduce` Optimus skipped overlay motions and called their hooks synchronously inside
 * an `afterRenderEffect`, which then re-ran the hooks whenever a signal they read changed (select lists that never
 * held still, toasts that never left). The app keeps the motion running; the CSS makes it instant.
 */
describe('overlay motion under reduced motion', () => {
  const originalSafe = DEFAULT_MOTION_OPTIONS.safe;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    window.matchMedia = ((query: string) => ({
      matches: query.includes('prefers-reduced-motion: reduce'),
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    DEFAULT_MOTION_OPTIONS.safe = originalSafe;
    window.matchMedia = originalMatchMedia;
  });

  it('the library default skips motions when the user prefers reduced motion', () => {
    expect(shouldSkipMotion(mergeOptions({}, DEFAULT_MOTION_OPTIONS))).toBe(true);
  });

  it('keeps every motion running, so its hooks never fire synchronously from the render effect', () => {
    runOverlayMotionUnderReducedMotion();
    expect(DEFAULT_MOTION_OPTIONS.safe).toBe(false);
    // the options a component passes (safe left undefined) fall back to the changed default
    expect(shouldSkipMotion(mergeOptions({ name: 'p-dialog', safe: undefined }, DEFAULT_MOTION_OPTIONS))).toBe(false);
  });

  it('runs the leave hooks only after the motion, not inside the call that starts it', async () => {
    runOverlayMotionUnderReducedMotion();
    const element = document.createElement('div');
    element.style.animationDuration = '1ms';
    element.style.animationName = 'fs-test';
    document.body.append(element);
    const afterLeave = vi.fn();
    const motion = createMotion(element, { name: 'p-dialog', onAfterLeave: afterLeave });
    const done = motion.leave();
    expect(afterLeave).not.toHaveBeenCalled();
    await done;
    expect(afterLeave).toHaveBeenCalledTimes(1);
    element.remove();
  });
});
