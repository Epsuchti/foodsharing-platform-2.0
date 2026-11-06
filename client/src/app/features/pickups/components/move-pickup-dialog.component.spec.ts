import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';

import { Pickup } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { MovePickupDialogComponent } from './move-pickup-dialog.component';

const AT = '2030-11-15T17:30:00Z';
const slot = (id: string, linked?: number) => ({
  user: { id, displayName: id },
  confirmed: true,
  ...(linked === undefined ? {} : { linkedIntroPickup: { introPickupId: 'e1', bookedCount: linked } }),
});
const PICKUP = {
  pickupAt: AT,
  slotCount: 3,
  isRegular: true,
  occupiedSlots: [slot('me'), slot('guide', 1), slot('other')],
} as unknown as Pickup;

/** PU10 (decision 170): the move dialog presets the current time, summarises who is told and maps 409. */
describe('MovePickupDialogComponent (PU10)', () => {
  let fixture: ComponentFixture<MovePickupDialogComponent>;
  let http: HttpTestingController;
  const byTestId = (id: string) => document.body.querySelector<HTMLElement>(`[data-testid="${id}"]`);
  const button = (id: string) => byTestId(id)?.querySelector('button') ?? null;

  async function open(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({}), provideRouter([]), provideOptimus(), MessageService],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovePickupDialogComponent);
    fixture.componentRef.setInput('storeId', 's1');
    fixture.componentRef.setInput('pickup', PICKUP);
    fixture.componentRef.setInput('timeZone', 'Europe/Zurich');
    fixture.componentRef.setInput('myId', 'me');
    fixture.componentInstance.visible.set(true);
    await fixture.whenStable();
  }

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
    document.body.innerHTML = '';
  });

  it('summarises the people told, the linked EinAbs and the cancelled regular occurrence', async () => {
    await open();
    expect(byTestId('pickups.move.summary')).not.toBeNull();
    expect(byTestId('pickups.move.introPickups')).not.toBeNull();
    expect(byTestId('pickups.move.regularHint')).not.toBeNull();
    expect(byTestId('pickups.move.error')).toBeNull();
  });

  it('moves to the preset time and shows PICKUP_TIME_TAKEN in the dialog', async () => {
    await open();
    let moved: Pickup | undefined;
    fixture.componentInstance.moved.subscribe((pickup) => (moved = pickup));
    button('pickups.move.save')!.click();
    const request = http.expectOne((req) => req.method === 'POST' && req.url.endsWith('/move'));
    expect(request.request.url).toBe(`/api/stores/s1/pickups/${encodeURIComponent(AT)}/move`);
    // the preset is the pickup's own time in the store zone; the server answers it is taken (here: the same time)
    expect(request.request.body).toEqual({ newPickupAt: AT });
    request.flush({ status: 409, code: 'PICKUP_TIME_TAKEN', message: 'taken' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    expect(byTestId('pickups.move.error')).not.toBeNull();
    expect(fixture.componentInstance.visible()).toBe(true);
    expect(moved).toBeUndefined();

    button('pickups.move.save')!.click();
    http.expectOne((req) => req.url.endsWith('/move')).flush({ ...PICKUP, pickupAt: '2030-11-16T10:00:00Z' });
    await fixture.whenStable();
    expect(moved?.pickupAt).toBe('2030-11-16T10:00:00Z');
    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
