import { animationsDone } from '../fixtures/a11y';
import { expect, forEachVariant, screenshotTest } from './_setup';

/**
 * Component gallery (/dev/gallery, plan §18.4): one element screenshot per `section[data-testid^="gallery."]`
 * × light/dark × de/fr, on the desktop and the mobile screenshot project. Design changes to a shared component
 * show up here once instead of on every page.
 */

screenshotTest.use({ seedReset: 'off' });

/** Number of tests the section captures are split into (see below). */
const GALLERY_PARTS = 4;

forEachVariant((variant) => {
  screenshotTest.describe(variant.name, () => {
    screenshotTest.use({ variant });

    // One element capture per section takes about a second; with 70+ sections a single test ran past the 60 s test
    // timeout on the desktop project. The sections are split into parts (contiguous slices of the page order, so
    // every section lands in exactly one part however many there are), each its own test with its own timeout.
    for (let part = 0; part < GALLERY_PARTS; part++) {
      screenshotTest(`gallery sections ${variant.name} part ${part + 1}/${GALLERY_PARTS} @gallery @screenshot`, async ({ page, visual }) => {
        await page.goto('/dev/gallery');
        await expect(page.locator('html')).toHaveAttribute('lang', variant.lang);
        await expect(page.getByTestId('gallery.live.state')).toHaveAttribute('data-state', 'success');

        // Element captures scroll the section into view; the sticky header would be painted over its top, and
        // sticky parts inside a section (timeline day titles) would stick mid-section when it is taller than the
        // viewport. A tall viewport (same width, so the same breakpoints) keeps every section fully in view.
        await page.addStyleTag({ content: 'fs-header { position: static !important; }' });
        const viewport = page.viewportSize()!;
        await page.setViewportSize({ width: viewport.width, height: 2400 });

        const sections = page.locator('section[data-testid^="gallery."]');
        const ids = await sections.evaluateAll((nodes) => nodes.map((node) => node.getAttribute('data-testid')!));
        expect(ids.length).toBeGreaterThan(20);
        expect(new Set(ids).size, 'gallery section test ids are unique').toBe(ids.length);

        const size = Math.ceil(ids.length / GALLERY_PARTS);
        for (const id of ids.slice(part * size, (part + 1) * size)) {
          const section = page.getByTestId(id);
          await section.scrollIntoViewIfNeeded();
          // The loading/refreshing specimens never settle on purpose; the live one was checked above.
          await visual.expectElement(section, `${id.replace(/^gallery\./, '')}-${variant.name}`, { waitForData: false });
        }
      });
    }

    screenshotTest(`gallery toast ${variant.name} @gallery @screenshot`, async ({ page, visual }) => {
      await page.goto('/dev/gallery');
      await page.getByTestId('gallery.toasts.success').click();
      const toast = page.locator('.p-toast-message').first();
      await expect(toast).toBeVisible();
      // the click scrolled the gallery to its buttons; the toast is fixed, so the page behind it would differ per run
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      // the toast slides in; capture it at rest
      await animationsDone(page);
      await visual.expectElement(toast, `toast-success-${variant.name}`, { waitForData: false });
    });
  });
});
