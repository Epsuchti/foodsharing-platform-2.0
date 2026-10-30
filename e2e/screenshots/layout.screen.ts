import type { Page } from '@playwright/test';
import { collapseSidenav } from '../fixtures';
import { expect, forEachVariant, screenshotTest, variants } from './_setup';

/**
 * Desktop side menu (decision 133, docs/design/sidenav-proposal.md §6.3): expanded (1280px) and rail (1000px) for the
 * newcomer, foodsaver, ambassador (bot) and orga, dark for foodsaver and orga, the floating cards (region switcher,
 * user menu, bell panel, rail flyout), the longest (fr) labels and the visitor menu. Every test sets a desktop
 * viewport, so the mobile project records the same screens with touch targets (44px rows). Baselines are generated in
 * Docker in the end batch (decision 69: `e2e/scripts/screenshots.sh --update-snapshots`), never per task.
 */

const WIDE = { width: 1280, height: 800 };
const LAPTOP = { width: 1000, height: 800 };

async function dashboard(page: Page, viewport: { width: number; height: number }): Promise<void> {
  await page.setViewportSize(viewport);
  await page.goto('/dashboard');
  await expect(page.getByTestId('nav.sidenav')).toBeVisible();
  await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
}

const ROLES = [
  { role: 'newbie', name: 'newcomer' },
  { role: 'foodsaver', name: 'foodsaver' },
  { role: 'bot', name: 'ambassador' },
  { role: 'orga', name: 'orga' },
] as const;

forEachVariant(
  (variant) => {
    for (const { role, name } of ROLES) {
      // dark only for the foodsaver and the orga (proposal §6.3)
      if (variant.theme === 'dark' && role !== 'foodsaver' && role !== 'orga') continue;
      screenshotTest.describe(`side menu ${name} ${variant.name}`, () => {
        screenshotTest.use({ variant, role });

        screenshotTest(
          `side menu expanded, ${name} @D4.3 @screenshot`,
          async ({ page, visual }) => {
            await dashboard(page, WIDE);
            if (role === 'newbie')
              await expect(page.getByTestId('nav.onboarding.progress')).toBeVisible();
            await visual.expectScreen(page, `sidenav-expanded-${name}-${variant.name}`, {
              fullPage: false,
            });
          },
        );

        screenshotTest(`side menu rail, ${name} @D4.3 @screenshot`, async ({ page, visual }) => {
          await dashboard(page, LAPTOP);
          await collapseSidenav(page);
          await visual.expectScreen(page, `sidenav-rail-${name}-${variant.name}`, {
            fullPage: false,
          });
        });
      });
    }
  },
  variants(['light', 'dark']),
);

forEachVariant((variant) => {
  screenshotTest.describe(`side menu cards ${variant.name}`, () => {
    screenshotTest.use({ variant, role: 'bot' });

    screenshotTest(
      'region switcher with regions and working groups @D4.3 @screenshot',
      async ({ page, visual }) => {
        await dashboard(page, WIDE);
        await page.getByTestId('shell.regionSwitcher').click();
        await expect(page.getByTestId('shell.regionSwitcher.card')).toBeVisible();
        await visual.expectScreen(page, `sidenav-region-switcher-${variant.name}`, {
          fullPage: false,
        });
      },
    );

    screenshotTest(
      'user menu with language, theme and shortcuts @D4.3 @screenshot',
      async ({ page, visual }) => {
        await dashboard(page, LAPTOP);
        await page.getByTestId('shell.userMenu').click();
        await page.getByTestId('shell.userMenu.language').click();
        await expect(page.getByTestId('shell.userMenu.language.de')).toBeVisible();
        await visual.expectScreen(page, `sidenav-user-menu-${variant.name}`, {
          fullPage: false,
        });
      },
    );

    screenshotTest(
      'bell panel next to the bell item @D4.3 @screenshot',
      async ({ page, visual }) => {
        await dashboard(page, WIDE);
        await page.getByTestId('bells.button').click();
        await expect(page.getByTestId('bells.panel.state')).toHaveAttribute(
          'data-state',
          /^(success|empty)$/,
        );
        await visual.expectScreen(page, `sidenav-bells-${variant.name}`, {
          fullPage: false,
        });
      },
    );

    screenshotTest(
      'rail with the Verwalten flyout open @D4.3 @screenshot',
      async ({ page, visual }) => {
        await page.setViewportSize({ width: 1000, height: 640 });
        await page.goto('/dashboard');
        await collapseSidenav(page);
        const flyout = page.getByTestId('nav.flyout.manage');
        await flyout.click();
        await expect(page.getByTestId('nav.flyout.manage.list')).toBeVisible();
        await visual.expectScreen(page, `sidenav-rail-flyout-${variant.name}`, {
          fullPage: false,
        });
      },
    );
  });

  screenshotTest.describe(`side menu visitor ${variant.name}`, () => {
    screenshotTest.use({ variant });

    screenshotTest(
      'visitor menu with language and appearance @D4.3 @screenshot',
      async ({ page, visual }) => {
        await page.setViewportSize(WIDE);
        await page.goto('/');
        await expect(page.getByTestId('public.landing.hero')).toBeVisible();
        await page.getByTestId('shell.prefs').click();
        await expect(page.getByTestId('shell.prefs.language.de')).toBeVisible();
        await visual.expectScreen(page, `sidenav-visitor-${variant.name}`, {
          fullPage: false,
        });
      },
    );
  });
});

// the longest labels: fr (proposal §4 "Long labels": they wrap, never ellipsis)
forEachVariant(
  (variant) => {
    screenshotTest.describe(`side menu orga fr ${variant.name}`, () => {
      screenshotTest.use({ variant, role: 'orga' });

      screenshotTest(
        'longest labels (fr) with Orga open @D4.3 @screenshot',
        async ({ page, visual }) => {
          await page.setViewportSize(WIDE);
          await page.goto('/admin/store-categories');
          await expect(page.getByTestId('nav.section.orga')).toHaveAttribute(
            'aria-expanded',
            'true',
          );
          await visual.expectScreen(page, `sidenav-orga-fr-${variant.name}`, {
            fullPage: false,
          });
        },
      );
    });
  },
  variants(['light'], ['fr']),
);
