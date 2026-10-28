import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { PhoneButtonComponent } from './phone-button.component';
import { PhoneInputComponent } from './phone-input.component';

@Component({
  imports: [PhoneInputComponent, PhoneButtonComponent, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-phone-input [formControl]="control" [mobile]="true" testId="t.phone" />
    <fs-phone-button [phone]="control.value" label="Anna" testId="t.call" />
  `,
})
class HostComponent {
  readonly control = new FormControl<string | null>('+41791234567');
}

describe('phone input and button', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: provideDataStateTesting() }));

  async function create() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('[data-testid="t.phone"]') as HTMLInputElement;
    return { fixture, el, input };
  }

  it('shows stored numbers readable and links them for calls', async () => {
    const { el, input } = await create();
    expect(input.value).toBe('+41 79 123 45 67');
    const call = el.querySelector('[data-testid="t.call"]') as HTMLAnchorElement;
    expect(call.getAttribute('href')).toBe('tel:+41791234567');
    expect(call.textContent).toContain('+41 79 123 45 67');
  });

  it('writes E.164 for valid input and reports invalid input', async () => {
    const { fixture, el, input } = await create();
    input.value = '044 123 45 67';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.componentInstance.control.value).toBe('+41441234567');
    expect(fixture.componentInstance.control.valid).toBe(true);
    // a Swiss landline where a mobile is expected gives a hint
    expect(el.querySelector('.hint')?.textContent).toContain('shared.phone.notMobile');

    input.value = '12-ab';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(fixture.componentInstance.control.errors).toEqual({ phoneNumber: true });
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(el.querySelector('[data-testid="t.call"]')).toBeNull();

    input.value = '';
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.componentInstance.control.value).toBeNull();
    expect(fixture.componentInstance.control.valid).toBe(true);
  });
});
