import { HttpTestingController } from '@angular/common/http/testing';
import { computed, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { vi } from 'vitest';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { StoreAutomationContext } from '../store-automation.context';
import { ExtraSlotSection } from './extra-slot.section';

describe('ExtraSlotSection: number of extra slots (AU6, decision 164)', () => {
  let http: HttpTestingController;
  const data = signal<{ maxExtraSlots: number; permissions: { mayEdit: boolean } } | undefined>(undefined);
  const reload = vi.fn();

  beforeEach(() => {
    reload.mockReset();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        provideOptimus(),
        {
          provide: StoreAutomationContext,
          useValue: {
            storeId: signal('s1'),
            data,
            mayEdit: computed(() => !!data()?.permissions.mayEdit),
            reload,
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function create(maxExtraSlots: number, mayEdit = true) {
    data.set({ maxExtraSlots, permissions: { mayEdit } });
    const fixture = TestBed.createComponent(ExtraSlotSection);
    await fixture.whenStable();
    http.match((r) => r.url.endsWith('/api/stores/s1/automation/audits')).forEach((r) =>
      r.flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } }),
    );
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const input = (el: HTMLElement) => el.querySelector<HTMLInputElement>('[data-testid="automation.extraSlot.max"] input')!;
  const save = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('[data-testid="automation.extraSlot.save"] button');

  it('shows the stored number and saves a changed one', async () => {
    const { fixture, el } = await create(2);
    expect(input(el).value).toBe('2');
    expect(save(el)!.disabled).toBe(true);

    (fixture.componentInstance as unknown as { draft: { set(v: number): void } }).draft.set(0);
    await fixture.whenStable();
    expect(save(el)!.disabled).toBe(false);
    save(el)!.click();
    const req = http.expectOne('/api/stores/s1/automation/extra-slot');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ maxExtraSlots: 0 });
    req.flush({ maxExtraSlots: 0 });
    await fixture.whenStable();
    expect(reload).toHaveBeenCalled();
  });

  it('is read-only without the right to edit', async () => {
    const { el } = await create(1, false);
    expect(input(el).disabled).toBe(true);
    expect(save(el)).toBeNull();
  });
});
