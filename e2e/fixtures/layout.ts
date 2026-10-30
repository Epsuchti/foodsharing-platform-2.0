import { expect, type Page } from '@playwright/test';

/**
 * Plan §18.3 "no overflowing text", the overflow audit of the design rounds (docs/design-review-m7.md) as an
 * assertion:
 * 1. the page never scrolls sideways (`documentElement.scrollWidth <= clientWidth`);
 * 2. no visible text is cut off sideways by a clipping container (`overflow: hidden/clip`, the viewport for
 *    everything else). Text inside a scroll container (`overflow: auto/scroll`, e.g. tab strips with a scroll hint)
 *    is reachable and not clipped; deliberate truncation (`text-overflow: ellipsis` on the clipping box), map tiles
 *    and visually hidden text (screen-reader-only labels in a 1 px box, text moved fully outside its box such as
 *    skip links) are not findings.
 * Both are soft assertions: one run lists every page and language with a finding, and the test still fails.
 */
export async function expectNoOverflow(page: Page, label: string): Promise<void> {
  await expect
    .soft
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), {
      message: `${label}: the page scrolls sideways`,
    })
    .toBeLessThanOrEqual(0);
  expect.soft(await clippedTextsOn(page), `${label}: text cut off sideways`).toEqual([]);
}

/** The clipped-text findings of `expectNoOverflow` (exported for the audit's own test). */
export function clippedTextsOn(page: Page): Promise<string[]> {
  return page.evaluate(clippedTexts);
}

/** In the page: descriptions of text elements whose text sticks out of the box that clips it. */
function clippedTexts(): string[] {
  const viewport = document.documentElement.clientWidth;
  const found: string[] = [];
  const ownText = (el: Element) => [...el.childNodes].some((node) => node.nodeType === Node.TEXT_NODE && node.textContent?.trim());
  for (const el of document.body.querySelectorAll('*')) {
    if (!ownText(el)) continue;
    if (el.closest('.leaflet-container, script, style, noscript, template')) continue;
    if (!el.checkVisibility({ opacityProperty: true, visibilityProperty: true })) continue;
    const range = document.createRange();
    range.selectNodeContents(el);
    const text = range.getBoundingClientRect();
    if (text.width < 1 || text.height < 1) continue;
    // the box that clips the text: the nearest hidden/clip ancestor (or the element itself), else the viewport
    let left = 0;
    let right = viewport;
    let reachable = false;
    let ellipsis = false;
    let visuallyHidden = false;
    for (let box: Element | null = el; box && box !== document.documentElement; box = box.parentElement) {
      const style = getComputedStyle(box);
      if (style.position === 'fixed' && box !== el) break;
      if (style.overflowX === 'auto' || style.overflowX === 'scroll') {
        reachable = true;
        break;
      }
      if (style.overflowX === 'hidden' || style.overflowX === 'clip') {
        const rect = box.getBoundingClientRect();
        left = rect.left + box.clientLeft;
        right = left + box.clientWidth;
        ellipsis = style.textOverflow === 'ellipsis';
        // screen-reader-only text (`.fs-visually-hidden`: a 1 px box that clips everything)
        visuallyHidden = rect.width <= 1 || rect.height <= 1;
        break;
      }
    }
    if (reachable || ellipsis || visuallyHidden) continue;
    const outside = text.right <= left || text.left >= right;
    if (outside) continue;
    if (text.right > right + 1 || text.left < left - 1) {
      const id = el.getAttribute('data-testid') ?? `${el.tagName.toLowerCase()}${[...el.classList].slice(0, 2).map((c) => `.${c}`).join('')}`;
      found.push(`${id} "${(el.textContent ?? '').trim().slice(0, 40)}" text ${Math.round(text.left)}–${Math.round(text.right)} box ${Math.round(left)}–${Math.round(right)}`);
      if (found.length >= 10) break;
    }
  }
  return found;
}
