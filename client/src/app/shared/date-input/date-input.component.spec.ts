import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { provideOptimus } from '@openng/optimus-ui/config';
import { DatePicker } from '@openng/optimus-ui/datepicker';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { typeIntoDateInput } from './date-input.testing';
import { DATE_INPUT_IMPORTS } from './index';

@Component({
  imports: [ReactiveFormsModule, ...DATE_INPUT_IMPORTS],
  template: `
    <label for="d">Date</label>
    <fs-date-input inputId="d" testId="t.date" [formControl]="date" min="2026-01-01" />
    <fs-datetime-input inputId="dt" testId="t.datetime" [formControl]="dateTime" />
    <fs-time-input inputId="tm" testId="t.time" [formControl]="time" [required]="true" />
  `,
})
class ReactiveHost {
  readonly date = new FormControl('2026-02-01', { nonNullable: true });
  readonly dateTime = new FormControl('2026-11-04T18:30', { nonNullable: true });
  readonly time = new FormControl('07:45', { nonNullable: true });
}

@Component({
  imports: [FormsModule, ...DATE_INPUT_IMPORTS],
  template: `<form><fs-date-input name="from" testId="t.tpl" [ngModel]="value()" (ngModelChange)="value.set($event)" /></form>`,
})
class TemplateHost {
  readonly value = signal('2026-05-17');
}

function setup<T>(host: new () => T) {
  TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
  return TestBed.createComponent(host);
}

function field(root: HTMLElement, testId: string): HTMLInputElement {
  return root.querySelector<HTMLInputElement>(`[data-testid="${testId}"] input`)!;
}

/** Types like a user into the wrapper around `input`. */
function type(input: HTMLInputElement, text: string): void {
  typeIntoDateInput(input.parentElement, text);
}

describe('fs-date-input / fs-datetime-input / fs-time-input (decision 150)', () => {
  it('shows the value in the Swiss format and keeps id and test id', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    expect(field(el, 't.date').value).toBe('01.02.2026');
    expect(field(el, 't.date').id).toBe('d');
    expect(field(el, 't.datetime').value).toBe('04.11.2026 18:30');
    expect(field(el, 't.time').value).toBe('07:45');
    expect(el.querySelector('label')!.htmlFor).toBe(field(el, 't.date').id);
  });

  it('turns typed text into the ISO value and re-renders it', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    type(field(el, 't.date'), '3.4.26');
    type(field(el, 't.datetime'), '2026-12-24 9:15');
    type(field(el, 't.time'), '8.05');
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('2026-04-03');
    expect(fixture.componentInstance.dateTime.value).toBe('2026-12-24T09:15');
    expect(fixture.componentInstance.time.value).toBe('08:05');
    expect(field(el, 't.date').value).toBe('03.04.2026');
    expect(fixture.componentInstance.date.touched).toBe(true);
  });

  it('shows an inline error for unreadable text and keeps the last valid value', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const input = field(el, 't.date');
    type(input, '31.02.2026');
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('2026-02-01');
    const error = el.querySelector('[data-testid="t.date"] [role="alert"]')!;
    expect(error.textContent).toContain('common.dateInput.invalid');
    expect(input.getAttribute('aria-describedby')).toBe(error.id);
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.value).toBe('31.02.2026');

    type(input, '02.01.2026');
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('2026-01-02');
    expect(el.querySelector('[data-testid="t.date"] [role="alert"]')).toBeNull();
    expect(input.hasAttribute('aria-invalid')).toBe(false);
  });

  it('keeps a typed value outside min/max (as native inputs did) and marks the field invalid', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const input = field(el, 't.date');
    type(input, '31.12.2025'); // before min
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('2025-12-31');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('[data-testid="t.date"] [role="alert"]')).toBeNull();
  });

  it('takes a calendar pick and a clear', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const pickers = fixture.debugElement.queryAll(By.directive(DatePicker)).map((d) => d.componentInstance as DatePicker);
    pickers[0].onSelect.emit(new Date(2026, 6, 9));
    pickers[1].onSelect.emit(new Date(2026, 6, 9, 20, 5));
    pickers[2].onSelect.emit(new Date(2000, 0, 1, 6, 0));
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('2026-07-09');
    expect(fixture.componentInstance.dateTime.value).toBe('2026-07-09T20:05');
    expect(fixture.componentInstance.time.value).toBe('06:00');
    expect(field(fixture.nativeElement, 't.date').value).toBe('09.07.2026');

    pickers[0].onClear.emit();
    await fixture.whenStable();
    expect(fixture.componentInstance.date.value).toBe('');
    expect(field(fixture.nativeElement, 't.date').value).toBe('');
  });

  it('passes the disabled state to the field and the calendar button', async () => {
    const fixture = setup(ReactiveHost);
    fixture.componentInstance.date.disable();
    await fixture.whenStable();
    const host = fixture.nativeElement.querySelector('[data-testid="t.date"]') as HTMLElement;
    expect(field(fixture.nativeElement, 't.date').disabled).toBe(true);
    expect(host.querySelector<HTMLButtonElement>('button')!.disabled).toBe(true);
    fixture.componentInstance.date.enable();
    await fixture.whenStable();
    expect(field(fixture.nativeElement, 't.date').disabled).toBe(false);
  });

  it('offers a labelled calendar button and no clear for required fields', async () => {
    const fixture = setup(ReactiveHost);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;
    const button = el.querySelector<HTMLButtonElement>('[data-testid="t.date"] button')!;
    expect(button.getAttribute('aria-label')).toBe('common.dateInput.openCalendar');
    expect(field(el, 't.date').placeholder).toBe('common.dateInput.placeholder');
    expect(field(el, 't.time').required).toBe(true);
  });

  it('works with ngModel in a template form', async () => {
    const fixture = setup(TemplateHost);
    await fixture.whenStable();
    const input = field(fixture.nativeElement, 't.tpl');
    expect(input.value).toBe('17.05.2026');
    type(input, '18.05.2026');
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toBe('2026-05-18');
    type(input, '');
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toBe('');
  });
});
