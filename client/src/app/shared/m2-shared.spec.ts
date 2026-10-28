import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideOptimus } from '@openng/optimus-ui/config';
import { of } from 'rxjs';

import { JobProgress, JobStatus } from '../api';
import { provideDataStateTesting } from '../core/testing/data-state-testing';
import { CalendarExportButtonComponent } from './calendar-export-button/calendar-export-button.component';
import { CsvDropzoneComponent, CsvFile } from './csv-dropzone/csv-dropzone.component';
import { LanguageTabsEditorComponent, TranslatedText } from './language-tabs-editor/language-tabs-editor.component';
import { MapViewComponent } from './map-view/map-view.component';
import { ProgressJobComponent } from './progress-job/progress-job.component';
import { RequiredMessageDialogComponent } from './required-message-dialog/required-message-dialog.component';

/** Specs of the smaller M2 shared components (T17). */

beforeEach(() => {
  TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
});

describe('LanguageTabsEditorComponent', () => {
  @Component({
    imports: [LanguageTabsEditorComponent, ReactiveFormsModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<fs-language-tabs-editor [formControl]="control" [requiredLanguages]="['de', 'fr']" testId="t.tabs" />`,
  })
  class Host {
    readonly control = new FormControl<TranslatedText>({ de: 'Hallo' });
  }

  it('edits one language per tab and requires the configured languages', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const control = fixture.componentInstance.control;
    expect(control.errors).toEqual({ languagesMissing: ['fr'] });

    (el.querySelector('[data-testid="t.tabs.tab.fr"]') as HTMLElement).click();
    await fixture.whenStable();
    const field = el.querySelector('[data-testid="t.tabs.field.fr"]') as HTMLInputElement;
    // untranslated languages show the German text as placeholder
    expect(field.placeholder).toBe('Hallo');
    field.value = 'Bonjour';
    field.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(control.value).toEqual({ de: 'Hallo', fr: 'Bonjour' });
    expect(control.valid).toBe(true);
  });
});

describe('RequiredMessageDialogComponent', () => {
  @Component({
    imports: [RequiredMessageDialogComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<fs-required-message-dialog [(visible)]="open" title="Verifizieren" (confirm)="sent.set($event)" testId="t.dialog" />`,
  })
  class Host {
    readonly open = signal(true);
    readonly sent = signal<string | null>(null);
  }

  it('needs at least 10 characters before confirming', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const body = document.body;
    const text = body.querySelector('[data-testid="t.dialog.message"]') as HTMLTextAreaElement;
    const confirm = body.querySelector('[data-testid="t.dialog.confirm"] button') as HTMLElement;
    text.value = 'zu kurz';
    text.dispatchEvent(new Event('input'));
    confirm.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.sent()).toBeNull();
    expect(text.getAttribute('aria-invalid')).toBe('true');

    text.value = '  Ausweis geprüft, alles gut.  ';
    text.dispatchEvent(new Event('input'));
    confirm.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.sent()).toBe('Ausweis geprüft, alles gut.');
  });
});

describe('CsvDropzoneComponent', () => {
  it('reads CSV files, counts rows and rejects other files', async () => {
    const fixture = TestBed.createComponent(CsvDropzoneComponent);
    fixture.componentRef.setInput('maxBytes', 200);
    const files: CsvFile[] = [];
    fixture.componentInstance.fileChange.subscribe((file) => files.push(file));
    await fixture.whenStable();
    const zone = fixture.componentInstance;

    await zone.read(new File(['﻿foodsaver_number;date\n100006;2026-10-01\n\n100007;2026-10-02\n'], 'past.csv', { type: 'text/csv' }));
    expect(files).toHaveLength(1);
    expect(files[0].rows).toBe(2);
    expect(files[0].text.startsWith('foodsaver_number')).toBe(true);

    await zone.read(new File(['x'], 'photo.png', { type: 'image/png' }));
    await zone.read(new File(['a'.repeat(500)], 'big.csv', { type: 'text/csv' }));
    await fixture.whenStable();
    expect(files).toHaveLength(1);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="shared.csv.error"]')).not.toBeNull();
  });
});

describe('ProgressJobComponent', () => {
  it('polls until the job is done and emits it', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProgressJobComponent);
    fixture.componentRef.setInput('jobId', 'job-1');
    fixture.componentRef.setInput('interval', 10);
    const finished: JobProgress[] = [];
    fixture.componentInstance.finished.subscribe((job) => finished.push(job));
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));

    http.expectOne((r) => r.url.endsWith('/api/jobs/job-1')).flush({ id: 'job-1', status: JobStatus.Running, total: 4, done: 1, resultAvailable: false });
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[role="progressbar"]')?.getAttribute('aria-valuenow')).toBe('25');

    await new Promise((resolve) => setTimeout(resolve, 20));
    http.expectOne((r) => r.url.endsWith('/api/jobs/job-1')).flush({ id: 'job-1', status: JobStatus.Done, total: 4, done: 4, resultAvailable: true });
    await fixture.whenStable();
    expect(finished.map((job) => job.status)).toEqual([JobStatus.Done]);
    expect(el.getAttribute('data-status')).toBe('DONE');
    // no further polling
    await new Promise((resolve) => setTimeout(resolve, 20));
    http.verify();
  });

  it('shows an unknown job as an error with retry', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ProgressJobComponent);
    fixture.componentRef.setInput('jobId', 'gone');
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    http
      .expectOne((r) => r.url.endsWith('/api/jobs/gone'))
      .flush({ code: 'JOB_NOT_FOUND', message: 'x', traceId: 't', retryable: false }, { status: 404, statusText: 'x' });
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('fs-error-state')).not.toBeNull();
  });
});

describe('CalendarExportButtonComponent', () => {
  it('downloads the ICS text and offers a webcal subscription', async () => {
    const fixture = TestBed.createComponent(CalendarExportButtonComponent);
    fixture.componentRef.setInput('ics', () => of('BEGIN:VCALENDAR\r\nEND:VCALENDAR'));
    fixture.componentRef.setInput('feedUrl', 'https://example.org/feed.ics');
    fixture.componentRef.setInput('testId', 't.cal');
    const clicks = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    const created = vi.fn(() => 'blob:x');
    (URL as unknown as { createObjectURL: () => string }).createObjectURL = created;
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('[data-testid="t.cal.subscribe"]')?.getAttribute('href')).toBe('webcal://example.org/feed.ics');
    (el.querySelector('[data-testid="t.cal"] button') as HTMLElement).click();
    await fixture.whenStable();
    expect(created).toHaveBeenCalled();
    expect(clicks).toHaveBeenCalled();
    clicks.mockRestore();
  });
});

describe('MapViewComponent', () => {
  it('draws typed markers and shows a tile failure with retry', async () => {
    const fixture = TestBed.createComponent(MapViewComponent);
    fixture.componentRef.setInput('markers', [
      { id: 'a', kind: 'store', lat: 47.37, lon: 8.54, label: 'Bäckerei' },
      { id: 'b', kind: 'region-pin', lat: 47.38, lon: 8.55, label: 'Zürich' },
    ]);
    fixture.componentRef.setInput('testId', 't.map');
    await fixture.whenStable();
    // Leaflet loads lazily
    await vi.waitFor(() => expect((fixture.nativeElement as HTMLElement).querySelectorAll('.fs-map-marker')).toHaveLength(2));
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.fs-map-marker[data-kind="store"]')).not.toBeNull();
    expect(el.textContent).toContain('Bäckerei');

    (fixture.componentInstance as unknown as { tiles: { fire(event: string): void } }).tiles.fire('tileerror');
    await fixture.whenStable();
    expect(el.getAttribute('data-tiles')).toBe('error');
    (el.querySelector('[data-testid="t.map.tileError"] button') as HTMLElement).click();
    await fixture.whenStable();
    expect(el.getAttribute('data-tiles')).toBe('loading');
  });
});
