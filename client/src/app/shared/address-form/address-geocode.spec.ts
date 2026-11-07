import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { AddressFormComponent, addressFormGroup } from './address-form.component';
import { AUTO_LOOKUP_DELAY_MS, addressKey, shouldAutoLookup } from './address-geocode';

const URL = '/api/map/geocode-address';

/** Decision 171 (D2): when the pin follows the typed address. */
describe('address geocoding rules', () => {
  it('needs street, postcode and city', () => {
    expect(addressKey({ street: 'Bahnhofstrasse 1', postcode: '8001', city: 'Zürich' })).toBe(
      'ch|bahnhofstrasse 1|8001|zürich',
    );
    expect(addressKey({ street: ' ', postcode: '8001', city: 'Zürich' })).toBeNull();
    expect(addressKey({ street: 'Bahnhofstrasse 1', postcode: '', city: 'Zürich' })).toBeNull();
    expect(addressKey({ street: 'Bahnhofstrasse 1', postcode: '8001', city: '' })).toBeNull();
    // spacing and case do not make a new address
    expect(addressKey({ street: 'Bahnhofstrasse  1 ', postcode: '8001', city: 'zürich', country: 'ch' })).toBe(
      addressKey({ street: 'Bahnhofstrasse 1', postcode: '8001', city: 'Zürich' }),
    );
  });

  it('never runs over a pin set by hand, without all fields or for an unchanged address', () => {
    expect(shouldAutoLookup('ch|a 1|8001|zürich', false, null)).toBe(true);
    expect(shouldAutoLookup('ch|a 1|8001|zürich', true, null)).toBe(false);
    expect(shouldAutoLookup(null, false, null)).toBe(false);
    expect(shouldAutoLookup('ch|a 1|8001|zürich', false, 'ch|a 1|8001|zürich')).toBe(false);
  });
});

describe('AddressFormComponent geocoding', () => {
  @Component({
    imports: [AddressFormComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<fs-address-form [group]="group" testId="t.address" />`,
  })
  class Host {
    readonly group = addressFormGroup();
  }

  let http: HttpTestingController;
  let fixture: ComponentFixture<Host>;
  const el = (testId: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const type = (field: string, value: string) => {
    const input = el(`t.address.${field}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const typeAddress = () => {
    type('street', 'Bahnhofstrasse 1');
    type('postcode', '8001');
    type('city', 'Zürich');
  };
  const lookups = () => http.match((req) => req.url === URL);

  beforeEach(async () => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify({ ignoreCancelled: true });
  });

  it('places the pin once street, postcode and city are typed in', async () => {
    typeAddress();
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS - 1);
    expect(lookups()).toHaveLength(0);
    vi.advanceTimersByTime(1);
    const [request] = lookups();
    const param = (name: string) => decodeURIComponent(request.request.params.get(name) ?? '');
    expect(param('street')).toBe('Bahnhofstrasse 1');
    expect(param('postcode')).toBe('8001');
    expect(param('city')).toBe('Zürich');
    // the server defaults to CH; the form sends its country field
    expect(param('country')).toBe('CH');
    request.flush({ label: 'Bahnhofstrasse 1, 8001 Zürich', lat: 47.3769, lon: 8.5402 });
    await vi.runAllTimersAsync();
    const group = fixture.componentInstance.group;
    expect([group.value.latitude, group.value.longitude]).toEqual([47.3769, 8.5402]);
    fixture.detectChanges();
    expect(el('t.address.geocodeStatus')?.getAttribute('data-status')).toBe('found');
  });

  it('does not look up with a field missing', () => {
    type('street', 'Bahnhofstrasse 1');
    type('postcode', '8001');
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS * 2);
    expect(lookups()).toHaveLength(0);
  });

  it('never moves a pin set by hand or loaded with the record', () => {
    fixture.componentInstance.group.patchValue({ latitude: 47.5, longitude: 8.7 });
    typeAddress();
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS * 2);
    expect(lookups()).toHaveLength(0);
    expect(fixture.componentInstance.group.value.latitude).toBe(47.5);
  });

  it('keeps following the address after its own pin', async () => {
    const group = fixture.componentInstance.group;
    typeAddress();
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS);
    lookups()[0].flush({ label: 'x', lat: 47.3769, lon: 8.5402 });
    await vi.runAllTimersAsync();
    type('street', 'Bahnhofstrasse 9');
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS);
    lookups()[0].flush({ label: 'y', lat: 47.3771, lon: 8.5399 });
    await vi.runAllTimersAsync();
    expect([group.value.latitude, group.value.longitude]).toEqual([47.3771, 8.5399]);
  });

  it('a running lookup does not move a pin set by hand meanwhile', async () => {
    const group = fixture.componentInstance.group;
    typeAddress();
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS);
    const [request] = lookups();
    group.patchValue({ latitude: 47.5, longitude: 8.7 });
    request.flush({ label: 'x', lat: 47.3769, lon: 8.5402 });
    await vi.runAllTimersAsync();
    expect([group.value.latitude, group.value.longitude]).toEqual([47.5, 8.7]);
  });

  it('does not look up a loaded address nobody typed', () => {
    fixture.componentInstance.group.patchValue({ street: 'Bahnhofstrasse 1', postcode: '8001', city: 'Zürich' });
    vi.advanceTimersByTime(AUTO_LOOKUP_DELAY_MS * 2);
    expect(lookups()).toHaveLength(0);
  });

  it('the button always looks up, also over a manual pin, and reports unknown and unavailable', async () => {
    const group = fixture.componentInstance.group;
    group.patchValue({ street: 'Nirgendweg 9', postcode: '9999', city: 'Nirgendwo', latitude: 47.5, longitude: 8.7 });
    (el('t.address.geocode')?.querySelector('button') as HTMLButtonElement).click();
    lookups()[0].flush(null, { status: 204, statusText: 'No Content' });
    await vi.runAllTimersAsync();
    fixture.detectChanges();
    expect(el('t.address.geocodeStatus')?.getAttribute('data-status')).toBe('notFound');
    expect(group.value.latitude).toBe(47.5);

    (el('t.address.geocode')?.querySelector('button') as HTMLButtonElement).click();
    lookups()[0].flush({ code: 'GEOCODING_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
    await vi.runAllTimersAsync();
    fixture.detectChanges();
    expect(el('t.address.geocodeStatus')?.getAttribute('data-status')).toBe('unavailable');

    (el('t.address.geocode')?.querySelector('button') as HTMLButtonElement).click();
    lookups()[0].flush({ label: 'x', lat: 47.1, lon: 8.1 });
    await vi.runAllTimersAsync();
    expect([group.value.latitude, group.value.longitude]).toEqual([47.1, 8.1]);
  });

  it('the button asks for the postcode first', () => {
    (el('t.address.geocode')?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(lookups()).toHaveLength(0);
    expect(el('t.address.geocodeStatus')?.getAttribute('data-status')).toBe('incomplete');
  });
});
