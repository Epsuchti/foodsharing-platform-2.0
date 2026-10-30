import { test, type Locator, type Page, type TestInfo } from '@playwright/test';
import { type ApiClient, expect, openNav } from '../../fixtures';

/**
 * Scripted persona walkthroughs (D11.5.5, decision 3: the usability test with real newcomers is replaced by AI
 * personas; design review M7 round 3). A `Walk` counts what the persona does by hand — steps (one task as the user
 * would name it), clicks/taps and filled fields — and records them, the duration and the friction found as test
 * annotations, so `test-results/results.json` carries the numbers of every run (desktop and phone).
 *
 * The budgets are the measured counts of round 3 (docs/design-review-m7.md): a change that makes a journey longer
 * fails the walk and has to be a deliberate decision (raise the budget with a note in the review).
 */
export interface Budget {
  steps: number;
  clicks: number;
  fields: number;
}

export class Walk {
  private steps = 0;
  private clicks = 0;
  private fields = 0;
  private readonly started = Date.now();
  private readonly friction: string[] = [];

  constructor(
    readonly page: Page,
    private readonly info: TestInfo,
    readonly persona: string,
  ) {}

  /** One step of the journey, as the persona would name it ("sign up for Thursday"). */
  async step<T>(title: string, body: () => Promise<T>): Promise<T> {
    this.steps++;
    return test.step(`${this.persona}: ${title}`, body);
  }

  /** A click/tap by the persona (setup through the API or other people's sessions is not counted). */
  async click(locator: Locator): Promise<void> {
    this.clicks++;
    await locator.click();
  }

  /** Checks a checkbox (one tap). */
  async check(locator: Locator): Promise<void> {
    this.clicks++;
    await locator.check();
  }

  /** Types into a field (one field, however long the text). */
  async fill(locator: Locator, value: string): Promise<void> {
    this.fields++;
    await locator.fill(value);
  }

  /** Opens a main navigation item like the persona would (tab, drawer or the header's "More" menu). */
  async nav(id: string): Promise<void> {
    this.clicks += await openNav(this.page, id);
  }

  /** Adds clicks done by a shared helper (e.g. answering a quiz) to the count. */
  count(clicks: number): void {
    this.clicks += clicks;
  }

  /** Friction that remains after round 3 (fixed friction is documented in the review, not here). */
  note(friction: string): void {
    this.friction.push(friction);
  }

  /** Records the numbers and asserts the round-3 budget of this persona. */
  finish(budget: Budget): void {
    const result = {
      persona: this.persona,
      project: this.info.project.name,
      steps: this.steps,
      clicks: this.clicks,
      fields: this.fields,
      durationMs: Date.now() - this.started,
      friction: this.friction,
    };
    this.info.annotations.push(
      { type: 'persona', description: this.persona },
      { type: 'steps', description: String(result.steps) },
      { type: 'clicks', description: String(result.clicks) },
      { type: 'fields', description: String(result.fields) },
      { type: 'duration', description: `${Math.round(result.durationMs / 1000)} s` },
      ...this.friction.map((description) => ({ type: 'friction', description })),
    );
    // eslint-disable-next-line no-console
    console.log(`[persona] ${JSON.stringify(result)}`);
    expect(result.steps, `${this.persona}: steps`).toBeLessThanOrEqual(budget.steps);
    expect(result.clicks, `${this.persona}: clicks`).toBeLessThanOrEqual(budget.clicks);
    expect(result.fields, `${this.persona}: fields`).toBeLessThanOrEqual(budget.fields);
  }
}

interface BellItem {
  id: string;
  type: string;
  link?: string;
  readAt?: string;
}

/**
 * The persona notices a new bell and opens it: the header bell panel on desktop, Menu → bells page on phones.
 * Waits for the newest unread bell of `type` (rung by someone else) and returns its link.
 */
export async function openNewestBell(walk: Walk, api: ApiClient, type: string): Promise<string> {
  const { page } = walk;
  let bell: BellItem | undefined;
  await expect
    .poll(async () => {
      const bells = (await api.getJson<{ items: BellItem[] }>('/api/bells?size=10')).items;
      bell = bells.find((item) => item.type === type && !item.readAt && item.link);
      return !!bell;
    }, { message: `unread ${type} bell` })
    .toBe(true);
  await page.reload();
  let item: string;
  if (isPhone(page)) {
    await walk.click(page.getByTestId('nav.tab.menu'));
    await walk.click(page.getByTestId('nav.drawer.bells'));
    await expect(page.getByTestId('bells.page.state')).toHaveAttribute('data-state', 'success');
    item = 'bells.page.item';
  } else {
    await walk.click(page.getByTestId('bells.button'));
    await expect(page.getByTestId('bells.panel.state')).toHaveAttribute('data-state', 'success');
    item = 'bells.panel.item';
  }
  await walk.click(page.locator(`[data-testid="${item}"][data-id="${bell!.id}"]`).getByTestId(`${item}.open`));
  const path = bell!.link!.split('?')[0];
  await expect(page).toHaveURL(new RegExp(path.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  return path;
}

/** True on the phone layout (bottom tab bar instead of the header navigation). */
export function isPhone(page: Page): boolean {
  return (page.viewportSize()?.width ?? 1280) < 768;
}
