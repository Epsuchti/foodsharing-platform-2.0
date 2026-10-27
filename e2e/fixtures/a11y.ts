import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

export const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

export interface A11yOptions {
  /** CSS selectors to restrict the scan to. */
  include?: string[];
  /** CSS selectors to leave out (e.g. third-party map canvas). */
  exclude?: string[];
  /** axe rule ids to disable; every use needs a comment explaining why. */
  disableRules?: string[];
}

/**
 * Runs axe (WCAG 2.0/2.1 A+AA, 2.2 AA) and fails on `serious`/`critical` violations (plan §18.3). The full axe result
 * is attached to the test. Runs automatically after every passing test tagged `@page` on its `page` fixture.
 */
export async function expectNoSeriousViolations(page: Page, options: A11yOptions = {}): Promise<void> {
  let builder = new AxeBuilder({ page }).withTags(AXE_TAGS);
  for (const selector of options.include ?? []) builder = builder.include(selector);
  for (const selector of options.exclude ?? []) builder = builder.exclude(selector);
  if (options.disableRules?.length) builder = builder.disableRules(options.disableRules);
  const results = await builder.analyze();
  await test.info().attach(`axe ${page.url()}`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: 'application/json',
  });
  const serious = results.violations.filter((violation) => violation.impact === 'serious' || violation.impact === 'critical');
  const summary = serious
    .map(
      (violation) =>
        `[${violation.impact}] ${violation.id}: ${violation.help}\n` +
        violation.nodes
          .slice(0, 5)
          .map((node) => `    ${node.target.join(' ')}`)
          .join('\n'),
    )
    .join('\n');
  expect(serious.length, `axe violations on ${page.url()}:\n${summary}`).toBe(0);
}

export const a11y = { expectNoSeriousViolations };
export type A11yHelpers = typeof a11y;
