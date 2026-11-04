import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { SUBJECT_PRESETS, SupportPageComponent } from './support-page.component';

describe('SupportPageComponent subject preset (C17, decision 159)', () => {
  let fixture: ComponentFixture<SupportPageComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'content.support.subjectPreset.partner': 'Partner werden: Lebensmittel abholen lassen',
          },
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SupportPageComponent);
  });

  afterEach(() => http.verify());

  const subjectField = () =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '[data-testid="content.support.subject"]',
    )!;

  it('prefills the subject for the allowlisted "partner" preset', async () => {
    fixture.componentRef.setInput('subject', 'partner');
    await fixture.whenStable();
    expect(subjectField().value).toBe('Partner werden: Lebensmittel abholen lassen');
  });

  it('ignores unknown presets and never copies free text from the URL', async () => {
    fixture.componentRef.setInput('subject', 'Hallo <b>Welt</b>');
    await fixture.whenStable();
    expect(subjectField().value).toBe('');
    fixture.componentRef.setInput('subject', 'toString');
    await fixture.whenStable();
    expect(subjectField().value).toBe('');
    expect(Object.keys(SUBJECT_PRESETS)).toEqual(['partner']);
  });

  it('prefills only once, so a later change keeps what the user typed', async () => {
    fixture.componentRef.setInput('subject', 'partner');
    await fixture.whenStable();
    const field = subjectField();
    field.value = 'Eigener Betreff';
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    fixture.componentRef.setInput('subject', undefined);
    fixture.componentRef.setInput('subject', 'partner');
    await fixture.whenStable();
    expect(subjectField().value).toBe('Eigener Betreff');
  });
});
