import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { RegularPickup, Weekday } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { WeekdayTimePickerComponent, duplicateRows } from './weekday-time-picker.component';

@Component({
  imports: [WeekdayTimePickerComponent],
  template: `<fs-weekday-time-picker [(rows)]="rows" testId="t" />`,
})
class Host {
  readonly rows = signal<RegularPickup[]>([{ weekday: Weekday.Monday, startTime: '19:00', slotCount: 3 }]);
}

describe('fs-weekday-time-picker (PU1)', () => {
  it('marks duplicate weekday/time rows', () => {
    const rows: RegularPickup[] = [
      { weekday: Weekday.Monday, startTime: '19:00', slotCount: 3 },
      { weekday: Weekday.Thursday, startTime: '19:00', slotCount: 3 },
      { weekday: Weekday.Monday, startTime: '19:00', slotCount: 2 },
    ];
    expect([...duplicateRows(rows)].sort()).toEqual([0, 2]);
  });

  it('adds rows, edits the time and flags the duplicate inline', async () => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelector('[data-testid="t.add"] button') as HTMLButtonElement).click();
    await fixture.whenStable();
    expect(fixture.componentInstance.rows()).toHaveLength(2);
    const time = el.querySelectorAll<HTMLInputElement>('[data-testid="t.time"]')[1];
    time.value = '19:00';
    time.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.componentInstance.rows()[1].startTime).toBe('19:00');
    expect(el.querySelectorAll('[data-testid="t.duplicate"]')).toHaveLength(2);
  });
});
