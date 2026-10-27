import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ApiErrorCode } from '../../api';
import { DataState, loadResource } from '../../core/data/data-state';
import {
  apiHttpError,
  controlledLoader,
  provideDataStateTesting,
} from '../../core/testing/data-state-testing';
import { DATA_STATE_IMPORTS } from './index';

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

describe('DataStateComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideDataStateTesting() });
  });

  async function create() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const root = () => fixture.nativeElement.querySelector('fs-data-state') as HTMLElement;
    return { fixture, root };
  }

  it('shows the skeleton while loading, then the data', async () => {
    const { fixture, root } = await create();
    expect(root().getAttribute('data-state')).toBe('loading');
    expect(root().querySelector('fs-skeleton')?.getAttribute('data-preset')).toBe('table');
    expect(root().querySelector('.item')).toBeNull();

    loader.resolve(['Brot', 'Gemüse']);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('success');
    expect([...root().querySelectorAll('.item')].map((li) => li.textContent)).toEqual([
      'Brot',
      'Gemüse',
    ]);
  });

  it('shows the empty state with its action', async () => {
    const { fixture, root } = await create();
    loader.resolve([]);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('empty');
    expect(root().textContent).toContain('Nichts da');
    (root().querySelector('[data-testid="test.list.action"] button') as HTMLButtonElement).click();
    expect(fixture.componentInstance.emptyClicks()).toBe(1);
  });

  it('shows the error with retry and recovers', async () => {
    const { fixture, root } = await create();
    loader.reject(apiHttpError(500, ApiErrorCode.UnexpectedError));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('error');
    expect(root().querySelector('fs-error-state')).not.toBeNull();

    (root().querySelector('[data-testid="test.list.retry"] button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('loading');
    loader.resolve(['Brot']);
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('success');
    expect(root().querySelector('fs-error-state')).toBeNull();
  });

  it('keeps stale data visible with a compact error after a failed reload', async () => {
    const { fixture, root } = await create();
    loader.resolve(['Brot']);
    await fixture.whenStable();
    fixture.componentInstance.state.reload();
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('refreshing');
    expect(root().querySelector('.item')).not.toBeNull();

    loader.reject(apiHttpError(503, ApiErrorCode.UnexpectedError));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('error');
    expect(root().querySelector('.item')).not.toBeNull();
    expect(root().querySelector('fs-error-state.fs-error-state--compact')).not.toBeNull();
  });

  it('renders forbidden without content', async () => {
    const { fixture, root } = await create();
    loader.reject(apiHttpError(403, ApiErrorCode.AccessDenied));
    await fixture.whenStable();
    expect(root().getAttribute('data-state')).toBe('forbidden');
    expect(root().querySelector('fs-error-state')?.getAttribute('data-kind')).toBe('forbidden');
  });
});
