import type { Page } from '@playwright/test';

/**
 * Waits until every finite CSS/Web animation (overlay enter, message fade-in) has finished, so axe and hit tests see
 * the final colors and positions. Infinite ones (skeleton shimmer, spinners) are ignored.
 */
export async function animationsDone(page: Page): Promise<void> {
  await page.evaluate(async () => {
    for (let round = 0; round < 5; round++) {
      const running = document
        .getAnimations()
        .filter((animation) => animation.effect?.getComputedTiming().iterations !== Infinity && animation.playState === 'running');
      if (!running.length) return;
      await Promise.all(running.map((animation) => animation.finished.catch(() => undefined)));
    }
  });
}
