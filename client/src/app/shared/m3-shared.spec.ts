import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { CooperationStatus, FreeSlotStatus, TeamSearchStatus, UserTeamStatus } from '../api';
import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { AddressFormComponent, addressFormGroup } from './address-form/address-form.component';
import { STORE_BADGES } from './store-badges/store-badges.component';

/** Specs of the M3 shared store components (F1). */

beforeEach(() => {
  TestBed.configureTestingModule({
    providers: [
      ...provideDataStateTesting({
        translations: {
          'shared.store.cooperation.COOPERATION_ESTABLISHED': 'Kooperiert',
          'shared.store.freeSlots.RED': '{count} frei',
          'shared.store.membership.WAITING_LIST': 'Springer:in',
        },
      }),
      provideOptimus(),
    ],
  });
});

describe('store badges', () => {
  @Component({
    imports: [...STORE_BADGES],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `
      <fs-cooperation-status-badge [status]="coop" />
      <fs-team-status-badge [status]="team" />
      <fs-membership-badge [status]="membership" />
      <fs-free-slot-dot [status]="slots" [count]="3" />
      <fs-free-slot-chip data-testid="chip.red" [status]="slots" [count]="3" />
      <fs-free-slot-chip data-testid="chip.orange" [status]="orange" [count]="2" />
      <fs-free-slot-chip data-testid="chip.yellow" [status]="yellow" [count]="1" />
      <fs-free-slot-chip data-testid="chip.green" [status]="green" [count]="0" />
    `,
  })
  class Host {
    readonly coop = CooperationStatus.CooperationEstablished;
    readonly team = TeamSearchStatus.OpenSearching;
    readonly membership = UserTeamStatus.WaitingList;
    readonly slots = FreeSlotStatus.Red;
    readonly orange = FreeSlotStatus.Orange;
    readonly yellow = FreeSlotStatus.Yellow;
    readonly green = FreeSlotStatus.Green;
  }

  it('renders translated labels, tones and an accessible free-slot dot', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const coop = el.querySelector('fs-cooperation-status-badge')!;
    expect(coop.textContent?.trim()).toBe('Kooperiert');
    expect(coop.classList.contains('chip--success')).toBe(true);
    expect(el.querySelector('fs-team-status-badge')!.classList.contains('chip--warn')).toBe(true);
    expect(el.querySelector('fs-membership-badge')!.textContent?.trim()).toBe('Springer:in');
    const dot = el.querySelector('fs-free-slot-dot .dot')!;
    expect(dot.getAttribute('role')).toBe('img');
    expect(dot.getAttribute('aria-label')).toBe('3 frei');
  });

  it('free-slot chip: text with tone and icon per status, nothing for GREEN (D5)', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const red = el.querySelector('[data-testid="chip.red"]')!;
    expect(red.classList.contains('chip--danger')).toBe(true);
    expect(red.textContent?.trim()).toBe('3 frei');
    expect(red.querySelector('i.pi-exclamation-circle')).not.toBeNull();
    expect(el.querySelector('[data-testid="chip.orange"]')!.classList.contains('chip--warn')).toBe(true);
    expect(el.querySelector('[data-testid="chip.yellow"]')!.classList.contains('chip--info')).toBe(true);
    const green = el.querySelector('[data-testid="chip.green"]')!;
    expect(green.classList.contains('chip--none')).toBe(true);
    expect(green.textContent?.trim()).toBe('');
  });
});

describe('AddressFormComponent', () => {
  @Component({
    imports: [AddressFormComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<fs-address-form [group]="group" testId="t.address" />`,
  })
  class Host {
    readonly group = addressFormGroup();
  }

  it('binds the address fields and shows the coordinates of the group', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const group = fixture.componentInstance.group;
    expect(group.valid).toBe(false);
    const street = el.querySelector('[data-testid="t.address.street"]') as HTMLInputElement;
    street.value = 'Bahnhofbrücke 1';
    street.dispatchEvent(new Event('input'));
    group.patchValue({ postcode: '8001', city: 'Zürich', latitude: 47.3779, longitude: 8.5427 });
    await fixture.whenStable();
    expect(group.valid).toBe(true);
    expect(group.value.street).toBe('Bahnhofbrücke 1');
    expect(el.querySelector('[data-testid="t.address.coordinates"]')!.textContent).toContain('47.37790, 8.54270');
  });
});
