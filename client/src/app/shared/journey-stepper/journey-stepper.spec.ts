import { HttpTestingController } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { DeferBlockState, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { Language, Role, Theme } from '../../api';
import { SessionService } from '../../core/auth/session.service';
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
          'shared.journey.typicalDays': 'dauert hier meist {days} Tage',
          'shared.journey.message': 'Nachricht',
          'shared.journey.messageAria': 'Nachricht an {name}',
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

  it('preview: a step without a duration shows none (S17 D9)', async () => {
    const { el } = await create({
      mode: 'preview',
      preview: DEMO_JOURNEY_PREVIEW.map((step) => (step.key === 'INTRO_PICKUPS' ? { ...step, estimatedDuration: undefined } : step)),
    });
    const steps = [...el.querySelectorAll('li')];
    const meta = steps.filter((step) => step.querySelector('.step__meta'));
    expect(meta.length).toBe(steps.length - 1);
  });

  it('shows who a waiting step waits for', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_WAITING });
    const waiting = el.querySelector('[data-step="ID_CHECK"]') as HTMLElement;
    expect(waiting.getAttribute('data-status')).toBe('WAITING_FOR_OTHERS');
    expect(waiting.querySelector('fs-user-chip')?.textContent).toContain('Reto Baumann');
  });

  it('a waiting step shows one time figure: the typical days, no estimate (review 2026-11-10 Q3)', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_WAITING });
    const waiting = el.querySelector('[data-step="ID_CHECK"]') as HTMLElement;
    expect(waiting.textContent).toContain('dauert hier meist 5 Tage');
    expect(waiting.querySelector('.step__meta')).toBeNull();
  });

  it('"Nachricht" opens the direct conversation with a waiting step contact (A4)', async () => {
    const { el, fixture } = await create({ journey: DEMO_JOURNEY_WAITING });
    // the button is loaded deferred (it keeps messaging out of the shell's initial bundle)
    for (const block of await fixture.getDeferBlocks()) await block.render(DeferBlockState.Complete);
    const button = el.querySelector('[data-testid="t.journey.message"]') as HTMLButtonElement;
    expect(button.textContent).toContain('Nachricht');
    expect(button.getAttribute('aria-label')).toBe('Nachricht an Reto Baumann');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    button.click();
    const request = TestBed.inject(HttpTestingController).expectOne('/api/conversations/lookup');
    expect(request.request.body).toEqual({ memberIds: ['00000001-0001-4000-8000-000000000011'] });
    request.flush({ id: 'c1' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/messages', 'c1']);
  });

  it('no "Nachricht" button for the user themselves', async () => {
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      units: [],
      user: {
        id: '00000001-0001-4000-8000-000000000011',
        email: 'reto@local',
        displayName: 'Reto Baumann',
        role: Role.Ambassador,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
    const { el, fixture } = await create({ journey: DEMO_JOURNEY_WAITING });
    expect(await fixture.getDeferBlocks()).toHaveLength(0);
    expect(el.querySelector('fs-user-chip')).not.toBeNull();
    expect(el.querySelector('[data-testid="t.journey.message"]')).toBeNull();
  });

  it('compact mode shows the step count and the next action', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS, mode: 'compact' });
    expect(el.textContent).toContain('Schritt 6 von 8');
    expect(el.querySelectorAll('.meter__seg')).toHaveLength(8);
    expect(el.querySelector('[data-testid="t.journey.next"]')?.getAttribute('href')).toBe('/intro-pickups');
  });

  it('compact progressOnly mode leaves the title and the action to the host card', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS, mode: 'compact', progressOnly: true });
    expect(el.textContent).toContain('Schritt 6 von 8');
    expect(el.querySelectorAll('.meter__seg')).toHaveLength(8);
    expect(el.textContent).not.toContain('Einführungsabholungen');
    expect(el.querySelector('[data-testid="t.journey.next"]')).toBeNull();
    expect(el.querySelector('[data-testid="t.journey.open"]')).toBeNull();
  });

  it('compact hideAction mode keeps the title but leaves the action to the page', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS, mode: 'compact', hideAction: true });
    expect(el.textContent).toContain('Schritt 6 von 8');
    expect(el.textContent).toContain('Einführungsabholungen');
    expect(el.querySelector('[data-testid="t.journey.next"]')).toBeNull();
    expect(el.querySelector('[data-testid="t.journey.open"]')).toBeNull();
  });

  it('full mode leaves out the action of the step the page shows itself', async () => {
    const { el } = await create({ journey: DEMO_JOURNEY_IN_PROGRESS, omitActionFor: 'INTRO_PICKUPS' });
    expect(el.querySelector('[data-testid="t.journey.action.INTRO_PICKUPS"]')).toBeNull();
    expect(el.querySelector('[aria-current="step"]')?.textContent).toContain('Einführungsabholungen');
  });

  it('preview mode lists the steps without status', async () => {
    const { el } = await create({ preview: DEMO_JOURNEY_PREVIEW, mode: 'preview' });
    expect(el.querySelectorAll('.step')).toHaveLength(8);
    expect(el.querySelector('[aria-current]')).toBeNull();
  });
});
