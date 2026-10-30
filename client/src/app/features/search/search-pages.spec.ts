import { HttpTestingController } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { SearchIndexData, SearchKind, SearchResults } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { GlobalSearchComponent } from './components/global-search.component';
import { SearchPageComponent } from './pages/search-page.component';

@Component({ selector: 'fs-test-target', template: 'target' })
class TargetComponent {}

@Component({ selector: 'fs-test-host', imports: [GlobalSearchComponent], template: '<fs-global-search />' })
class HostComponent {}

const INDEX: SearchIndexData = {
  regions: [],
  groups: [],
  stores: [{ id: 's1', name: 'Bäckerei Wiedikon', link: '/stores/s1' }],
  foodSharePoints: [{ id: 'f1', name: 'Fairteiler Wiedikon', link: '/food-share-points/f1' }],
  conversations: [],
};

function results(overrides: Partial<SearchResults> = {}): SearchResults {
  return { users: [], regions: [], groups: [], stores: [], threads: [], foodSharePoints: [], events: [], polls: [], ...overrides };
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe('search', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector) as HTMLElement | null;
  const all = (selector: string) => Array.from(harness.routeNativeElement!.querySelectorAll(selector)) as HTMLElement[];
  const settle = () => harness.fixture.whenStable();
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const searchRequest = (req: { url: string }) => req.url === '/api/search';

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'host', component: HostComponent },
            { path: 'search', component: SearchPageComponent },
            { path: '**', component: TargetComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  describe('header search', () => {
    const input = () => q('[data-testid="search.global.input"]') as HTMLInputElement;
    const type = async (value: string) => {
      input().value = value;
      input().dispatchEvent(new Event('input'));
      await settle();
    };
    const key = async (name: string) => {
      input().dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
      await settle();
    };

    beforeEach(async () => {
      await harness.navigateByUrl('/host');
      await settle();
      input().dispatchEvent(new Event('focus'));
      await settle();
      http.expectOne('/api/search/index').flush(INDEX);
      await settle();
    });

    it('is a combobox that shows index results instantly and asks the server after 300 ms from two characters', async () => {
      expect(input().getAttribute('role')).toBe('combobox');
      expect(input().getAttribute('aria-expanded')).toBe('false');
      await type('wiedikon');
      expect(input().getAttribute('aria-expanded')).toBe('true');
      expect(all('[data-testid="search.global.group"]').map((group) => group.dataset['group'])).toEqual(['index.stores', 'index.foodSharePoints']);
      http.expectNone(searchRequest);

      await wait(350);
      const request = http.expectOne(searchRequest);
      expect(request.request.params.get('q')).toBe('wiedikon');
      request.flush(
        results({
          users: [{ id: 'u1', kind: SearchKind.User, title: 'Nina Schneider', link: '/profile/u1' }],
          // already in the index: shown once
          stores: [{ id: 's1', kind: SearchKind.Store, title: 'Bäckerei Wiedikon', link: '/stores/s1' }],
        }),
      );
      await settle();
      expect(all('[data-testid="search.global.group"]').map((group) => group.dataset['group'])).toEqual([
        'index.stores',
        'index.foodSharePoints',
        'remote.users',
      ]);
      expect(all('[data-testid="search.global.option"]')).toHaveLength(3);
    });

    it('does not ask the server for one character and debounces fast typing', async () => {
      await type('w');
      expect(q('[data-testid="search.global.tooShort"]')).toBeNull(); // index hits exist
      await wait(350);
      http.expectNone(searchRequest);
      await type('xy');
      await wait(100);
      await type('xyz');
      await wait(350);
      const request = http.expectOne(searchRequest);
      expect(request.request.params.get('q')).toBe('xyz');
      request.flush(results());
      await settle();
      expect(state('search.global.state')).toBe('empty');
      expect(q('[data-testid="search.global.empty"]')).not.toBeNull();
    });

    it('moves through the options with the arrow keys and opens the active one with Enter', async () => {
      await type('wiedikon');
      await key('ArrowDown');
      await key('ArrowDown');
      const active = input().getAttribute('aria-activedescendant');
      expect(active).toBeTruthy();
      expect(document.getElementById(active!)?.getAttribute('aria-selected')).toBe('true');
      expect(document.getElementById(active!)?.dataset['link']).toBe('/food-share-points/f1');
      await key('Enter');
      await settle();
      expect(TestBed.inject(Router).url).toBe('/food-share-points/f1');
    });

    it('sets aria-activedescendant synchronously while handling the arrow key', async () => {
      await type('wiedikon');
      input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
      // no settle(): the next scheduled change detection may come late on a busy main thread
      const active = input().getAttribute('aria-activedescendant');
      expect(active).toBeTruthy();
      expect(document.getElementById(active!)?.getAttribute('aria-selected')).toBe('true');
      expect(document.getElementById(active!)?.dataset['link']).toBe('/stores/s1');
      http.match(searchRequest).forEach((request) => request.flush(results()));
    });

    it('keeps the active option when the server hits arrive', async () => {
      await type('wiedikon');
      await key('ArrowDown');
      await key('ArrowDown');
      await key('ArrowDown');
      // the third option is "show all results"
      expect(input().getAttribute('aria-activedescendant')).toMatch(/-all$/);
      await wait(350);
      http.expectOne(searchRequest).flush(
        results({ users: [{ id: 'u1', kind: SearchKind.User, title: 'Nina Schneider', link: '/profile/u1' }] }),
      );
      await settle();
      expect(all('[data-testid="search.global.option"]')).toHaveLength(3);
      expect(input().getAttribute('aria-activedescendant')).toMatch(/-all$/);
      await key('ArrowUp');
      expect(document.getElementById(input().getAttribute('aria-activedescendant')!)?.dataset['link']).toBe('/profile/u1');
    });

    it('sends Enter without an active option to /search?q= and closes with Escape', async () => {
      await type('wiedikon');
      await key('Escape');
      expect(input().getAttribute('aria-expanded')).toBe('false');
      expect(input().value).toBe('wiedikon');
      await key('Escape');
      expect(input().value).toBe('');
      await type('nina');
      await key('Enter');
      await settle();
      expect(TestBed.inject(Router).url).toBe('/search?q=nina');
      http.match(searchRequest).forEach((request) => request.flush(results()));
    });

    it('shows the server error with retry', async () => {
      await type('nina');
      await wait(350);
      http.expectOne(searchRequest).flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('search.global.state')).toBe('error');
    });
  });

  describe('search page', () => {
    it('shows a hint below two characters and loads grouped results for ?q=', async () => {
      await harness.navigateByUrl('/search');
      await settle();
      expect(q('[data-testid="search.page.hint"]')).not.toBeNull();
      http.expectNone(searchRequest);

      await harness.navigateByUrl('/search?q=nina');
      await settle();
      expect(state('search.page.state')).toBe('loading');
      http.expectOne(searchRequest).flush(
        results({
          users: [{ id: 'u1', kind: SearchKind.User, title: 'Nina Schneider', subtitle: 'Zürich Stadt', link: '/profile/u1' }],
          stores: [{ id: 's9', kind: SearchKind.Store, title: 'Nina’s Bistro', link: '/stores/s9' }],
        }),
      );
      await settle();
      expect(state('search.page.state')).toBe('success');
      expect(all('[data-testid="search.page.group"]').map((group) => group.dataset['group'])).toEqual(['remote.users', 'remote.stores']);
      expect((q('[data-testid="search.page.input"]') as HTMLInputElement).value).toBe('nina');

      // kind chips filter the shown groups
      (q('[data-testid="search.page.kind"][data-kind="STORE"]') as HTMLButtonElement).click();
      await settle();
      expect(all('[data-testid="search.page.group"]').map((group) => group.dataset['group'])).toEqual(['remote.stores']);
    });

    it('shows the empty state and the error state with retry', async () => {
      await harness.navigateByUrl('/search?q=zzz');
      await settle();
      http.expectOne(searchRequest).flush(results());
      await settle();
      expect(state('search.page.state')).toBe('empty');

      await harness.navigateByUrl('/search?q=yyy');
      await settle();
      http.expectOne(searchRequest).flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('search.page.state')).toBe('error');
      (q('[data-testid="search.page.state"] [data-testid$=".retry"] button') as HTMLButtonElement).click();
      await settle();
      http.expectOne(searchRequest).flush(results({ polls: [{ id: 'p1', kind: SearchKind.Poll, title: 'Yyy', link: '/polls/p1' }] }));
      await settle();
      expect(state('search.page.state')).toBe('success');
    });
  });
});
