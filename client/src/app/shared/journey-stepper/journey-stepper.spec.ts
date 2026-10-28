import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { JourneyStepperComponent } from './journey-stepper.component';
import {
  DEMO_JOURNEY_IN_PROGRESS,
  DEMO_JOURNEY_PREVIEW,
  DEMO_JOURNEY_WAITING,
} from './testing/journey-demo';

@Component({ template: '' })
class BlankComponent {}

describe('JourneyStepperComponent', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: provideDataStateTesting({
        routes: [{ path: '**', component: BlankComponent }],
        translations: {
          'onboarding.step.INTRO_PICKUPS.title': 'Einführungsabholungen',
          'onboarding.action.bookIntroPickup': 'Termin buchen',
          'onboarding.blocked.FAIRTEILER_VISIT_REQUIRED': 'Fairteiler nötig',
          'shared.journey.stepOf': 'Schritt {n} von {total}',
          'shared.journey.duration.weeks': '{count, plural, one {ca. # Woche} other {ca. # Wochen}}',
        },
      }),
    }),
  );

  async function create(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(JourneyStepperComponent);
    fixture.componentRef.setInput('testId', 't.journey');
    for (const [name, value] of Object.entries(inputs)) fixture.componentRef.setInput(name, value);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('full mode marks the current step with progress, blocked note and next action', async () => {
    const { el, fixture } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS });
    const current = el.querySelector('[aria-current="step"]') as HTMLElement;
    expect(current.getAttribute('data-step')).toBe('INTRO_PICKUPS');
    expect(current.textContent).toContain('Einführungsabholungen');
    expect(current.textContent).toContain('Fairteiler nötig');
    expect(current.textContent).toContain('ca. 2 Wochen');
    expect(el.querySelector('[data-testid="t.journey.progress.INTRO_PICKUPS"]')).not.toBeNull();
    const action = el.querySelector('[data-testid="t.journey.action.INTRO_PICKUPS"]') as HTMLAnchorElement;
    expect(action.getAttribute('href')).toBe('/intro-pickups');
    const emitted: unknown[] = [];
    fixture.componentInstance.action.subscribe((next) => emitted.push(next));
    action.click();
    await fixture.whenStable();
    expect(emitted).toHaveLength(1);
    // done and locked steps show no details
    expect(el.querySelector('[data-step="ACCOUNT"] .step__text')).toBeNull();
    expect(el.querySelectorAll('[data-status="DONE"]')).toHaveLength(5);
  });

  it('shows who a waiting step waits for', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_WAITING });
    const waiting = el.querySelector('[data-step="ID_CHECK"]') as HTMLElement;
    expect(waiting.getAttribute('data-status')).toBe('WAITING_FOR_OTHERS');
    expect(waiting.querySelector('fs-user-chip')?.textContent).toContain('Reto Baumann');
  });

  it('compact mode shows the step count and the next action', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS, mode: 'compact' });
    expect(el.textContent).toContain('Schritt 6 von 8');
    expect(el.querySelectorAll('.meter__seg')).toHaveLength(8);
    expect(el.querySelector('[data-testid="t.journey.next"]')?.getAttribute('href')).toBe('/intro-pickups');
  });

  it('preview mode lists the steps without status', async () => {
    const { el } = await create({ preview: DEMO_JOURNEY_PREVIEW, mode: 'preview' });
    expect(el.querySelectorAll('.step')).toHaveLength(8);
    expect(el.querySelector('[aria-current]')).toBeNull();
  });
});
