import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ApiErrorCode } from '../../api';
import { DataState, loadResource } from '../../core/data/data-state';
import { OnlineStatusService } from '../../core/online/online-status.service';
import {
  TestingOnlineStatusService,
  apiHttpError,
  controlledLoader,
  networkError,
  provideDataStateTesting,
} from '../../core/testing/data-state-testing';
import { DATA_STATE_IMPORTS } from './index';

/**
 * The shared state spec (decision 180 #5): every state a data-loading element shows, asserted once here for
 * `loadResource` + `<fs-data-state>` instead of once per page. Every page wires its data through these two
 * (client/scripts/check-data-state.mjs); the e2e side is the things-go-wrong journey, the captures are in the gallery
 * journey. The status mapping and reload rules of `loadResource` alone: core/data/data-state.spec.ts; the error
 * views: error-state.spec.ts; mutations: core/data/mutation.spec.ts.
 */

const loader = controlledLoader<string[]>();

@Component({
  imports: [...DATA_STATE_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="state"
      skeleton="table"
      emptyTitle="Nichts da"
      emptyActionLabel="Suchen"
      askWho="Frag die Botschafterin."
      backLink="/dashboard"
      (emptyAction)="emptyClicks.set(emptyClicks() + 1)"
      data-testid="test.list"
    >
      <ng-template [fsData]="state" let-items>
        <ul>
          @for (item of items; track item) {
            <li class="item">{{ item }}</li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
})
class HostComponent {
  readonly state: DataState<string[]> = loadResource({ loader: loader.loader });
  readonly emptyClicks = signal(0);
}

const paramsLoader = controlledLoader<string[]>();

/** Params-driven state with the custom templates (`fsSkeleton`, `fsEmpty`, `fsError`). */
@Component({
  imports: [...DATA_STATE_IMPORTS],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="state" data-testid="test.custom">
      <ng-template fsSkeleton><p class="my-skeleton">…</p></ng-template>
      <ng-template fsEmpty><p class="my-empty">Leer</p></ng-template>
      <ng-template fsError let-error let-retry="retry">
        <button class="my-retry" type="button" (click)="retry()">{{ error?.code }}</button>
      </ng-template>
      <ng-template [fsData]="state" let-items>
        <p class="my-data">{{ items.join(', ') }}</p>
      </ng-template>
    </fs-data-state>
  `,
})
class CustomHostComponent {
  readonly id = signal<string | undefined>(undefined);
  readonly state: DataState<string[]> = loadResource({
    params: () => this.id(),
    loader: paramsLoader.loader,
  });
}

describe('fs-data-state + loadResource (every state)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
  });

  async function create() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const root = () => fixture.nativeElement.querySelector('fs-data-state') as HTMLElement;
    const retry = () => root().querySelector('[data-testid="test.list.retry"] button') as HTMLButtonElement | null;
    return { fixture, root, retry };
  }

  async function loaded(items: string[]) {
    const created = await create();
    loader.resolve(items);
    await created.fixture.whenStable();
    return created;
  }

  it('loading: shows the skeleton, busy, no content', async () => {
    const { root } = await create();
    expect(root().getAttribute('data-state')).toBe('loading');
    expect(root().getAttribute('aria-busy')).toBe('true');
    expect(root().querySelector('[role="status"] fs-skeleton')?.getAttribute('data-preset')).toBe('table');
    expect(root().querySelector('.item')).toBeNull();
    expect(root().querySelector('fs-error-state')).toBeNull();
  });

  it('success: shows the data, not busy', async () => {
    const { root } = await loaded(['Brot', 'Gemüse']);
    expect(root().getAttribute('data-state')).toBe('success');
    expect(root().getAttribute('aria-busy')).toBe('false');
    expect([...root().querySelectorAll('.item')].map((li) => li.textContent)).toEqual(['Brot', 'Gemüse']);
    expect(root().querySelector('fs-skeleton')).toBeNull();
  });

  it('refreshing: keeps the data with an inline spinner, then shows the new data', async () => {
    const { fixture, root } = await loaded(['Brot']);
    fixture.componentInstance.state.reload();
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('refreshing');
    expect(root().getAttribute('aria-busy')).toBe('true');
    expect(root().querySelector('.ds__refreshing')).not.toBeNull();
    expect(root().querySelector('fs-skeleton')).toBeNull();
    expect(root().querySelector('.item')?.textContent).toBe('Brot');

    loader.resolve(['Brot', 'Käse']);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('success');
    expect(root().querySelector('.ds__refreshing')).toBeNull();
    expect(root().querySelectorAll('.item')).toHaveLength(2);
  });

  it('empty: shows the empty state with its action', async () => {
    const { fixture, root } = await loaded([]);
    expect(root().getAttribute('data-state')).toBe('empty');
    expect(root().textContent).toContain('Nichts da');
    expect(root().querySelector('.item')).toBeNull();
    (root().querySelector('[data-testid="test.list.action"] button') as HTMLButtonElement).click();
    expect(fixture.componentInstance.emptyClicks()).toBe(1);
  });

  it('error: shows the error with retry, which loads again and recovers', async () => {
    const { fixture, root, retry } = await create();
    loader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('error');
    expect(root().querySelector('fs-error-state')?.getAttribute('data-kind')).toBe('error');
    expect(root().querySelector('.item')).toBeNull();
    const callsBefore = loader.calls;

    retry()!.click();
    await fixture.whenStable();
    expect(loader.calls).toBe(callsBefore + 1);
    expect(root().getAttribute('data-state')).toBe('loading');
    loader.resolve(['Brot']);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('success');
    expect(root().querySelector('fs-error-state')).toBeNull();
  });

  it('error after a reload: keeps the stale data visible under a compact error', async () => {
    const { fixture, root } = await loaded(['Brot']);
    fixture.componentInstance.state.reload();
    await fixture.whenStable();
    loader.reject(apiHttpError(503, ApiErrorCode.UnexpectedError));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('error');
    expect(root().querySelector('.item')).not.toBeNull();
    expect(root().querySelector('.ds__content--stale')).not.toBeNull();
    expect(root().querySelector('fs-error-state.fs-error-state--compact')).not.toBeNull();
  });

  it('forbidden: no content, who to ask, a way back, no retry', async () => {
    const { fixture, root, retry } = await create();
    loader.reject(apiHttpError(403, ApiErrorCode.AccessDenied));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('forbidden');
    expect(root().querySelector('fs-error-state')?.getAttribute('data-kind')).toBe('forbidden');
    expect(root().textContent).toContain('Frag die Botschafterin.');
    expect(root().querySelector('[data-testid="test.list.back"]')?.getAttribute('href')).toBe('/dashboard');
    expect(retry()).toBeNull();
    expect(root().querySelector('.item')).toBeNull();
  });

  it('forbidden after a reload: drops the data it showed before', async () => {
    const { fixture, root } = await loaded(['Geheim']);
    fixture.componentInstance.state.reload();
    await fixture.whenStable();
    loader.reject(apiHttpError(403, ApiErrorCode.AccessDenied));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('forbidden');
    expect(root().querySelector('.item')).toBeNull();
  });

  it('not found: no content, a way back, no retry', async () => {
    const { fixture, root, retry } = await create();
    loader.reject(apiHttpError(404, ApiErrorCode.ResourceNotFound));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('notFound');
    expect(root().querySelector('fs-error-state')?.getAttribute('data-kind')).toBe('notFound');
    expect(root().querySelector('[data-testid="test.list.back"]')?.getAttribute('href')).toBe('/dashboard');
    expect(retry()).toBeNull();
    expect(root().querySelector('.item')).toBeNull();
  });

  it('offline: shows the offline message with retry and reloads by itself once back online', async () => {
    const online = TestBed.inject(OnlineStatusService) as unknown as TestingOnlineStatusService;
    const { fixture, root, retry } = await create();
    online.setOnline(false);
    loader.reject(networkError());
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('offline');
    expect(root().querySelector('fs-error-state')?.getAttribute('data-kind')).toBe('offline');
    expect(retry()).not.toBeNull();
    const callsBefore = loader.calls;

    online.setOnline(true);
    await fixture.whenStable();
    expect(loader.calls).toBe(callsBefore + 1);
    loader.resolve(['Brot']);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('success');
    expect(root().querySelector('fs-error-state')).toBeNull();
  });

  it('offline after a reload: keeps the stale data visible under a compact offline message', async () => {
    const { fixture, root, retry } = await loaded(['Brot']);
    fixture.componentInstance.state.reload();
    await fixture.whenStable();
    loader.reject(networkError());
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('offline');
    expect(root().querySelector('.item')?.textContent).toBe('Brot');
    expect(root().querySelector('.ds__content--stale')).not.toBeNull();
    expect(root().querySelector('fs-error-state.fs-error-state--compact')?.getAttribute('data-kind')).toBe('offline');
    expect(retry()).not.toBeNull();
  });

  describe('params and custom templates', () => {
    async function createCustom() {
      const fixture = TestBed.createComponent(CustomHostComponent);
      await fixture.whenStable();
      const root = () => fixture.nativeElement.querySelector('fs-data-state') as HTMLElement;
      return { fixture, root };
    }

    it('idle without params: renders nothing and loads nothing', async () => {
      const callsBefore = paramsLoader.calls;
      const { root } = await createCustom();
      expect(root().getAttribute('data-state')).toBe('idle');
      expect(root().textContent?.trim()).toBe('');
      expect(paramsLoader.calls).toBe(callsBefore);
    });

    it('uses the custom skeleton, data, empty and error templates; the error template gets retry', async () => {
      const { fixture, root } = await createCustom();
      fixture.componentInstance.id.set('a');
      await fixture.whenStable();
      expect(root().getAttribute('data-state')).toBe('loading');
      expect(root().querySelector('.my-skeleton')).not.toBeNull();
      expect(root().querySelector('fs-skeleton')).toBeNull();

      paramsLoader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
      await fixture.whenStable();
      expect(root().getAttribute('data-state')).toBe('error');
      expect(root().querySelector('fs-error-state')).toBeNull();
      const retry = root().querySelector('.my-retry') as HTMLButtonElement;
      expect(retry.textContent).toBe(ApiErrorCode.UnexpectedError);

      retry.click();
      await fixture.whenStable();
      paramsLoader.resolve([]);
      await fixture.whenStable();
      expect(root().getAttribute('data-state')).toBe('empty');
      expect(root().querySelector('.my-empty')).not.toBeNull();

      // new params: the skeleton again (no stale content of the old params), then the data
      fixture.componentInstance.id.set('b');
      await fixture.whenStable();
      expect(root().getAttribute('data-state')).toBe('loading');
      paramsLoader.resolve(['Brot', 'Käse']);
      await fixture.whenStable();
      expect(root().getAttribute('data-state')).toBe('success');
      expect(root().querySelector('.my-data')?.textContent).toBe('Brot, Käse');
    });
  });
});
