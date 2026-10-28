import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { Button } from '@openng/optimus-ui/button';
import { Observable } from 'rxjs';

import { runMutation } from '../../core/data/mutation';
import { TPipe } from '../../core/i18n/t.pipe';

/** Saves an iCalendar text as a `.ics` file; opening it adds the event to the calendar app. */
export function downloadIcs(document: Document, ics: string, fileName: string): void {
  const view = document.defaultView;
  if (!view?.URL?.createObjectURL) {
    return;
  }
  const url = view.URL.createObjectURL(new Blob([ics], { type: 'text/calendar;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
  view.setTimeout(() => view.URL.revokeObjectURL(url), 1000);
}

/**
 * "Add to calendar" (§12.4 CalendarExportButton: pickups, intro pickups, events). `ics` loads the calendar text from a
 * generated API call (e.g. `getIntroPickupIcs`); `feedUrl` additionally offers a subscription link (`webcal:`).
 *
 * `<fs-calendar-export-button [ics]="() => api.getIntroPickupIcs({ id })" fileName="intro-pickup.ics" />`
 */
@Component({
  selector: 'fs-calendar-export-button',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-calendar-export-button' },
  template: `
    <p-button
      [label]="label() ?? ('shared.calendar.add' | t)"
      icon="pi pi-calendar-plus"
      severity="secondary"
      [outlined]="true"
      [size]="small() ? 'small' : undefined"
      [loading]="download.busy()"
      (onClick)="download.run()"
      [attr.data-testid]="testId()"
    />
    @if (webcalUrl(); as url) {
      <a class="feed" [href]="url" [attr.data-testid]="testId() + '.subscribe'">
        <i class="pi pi-sync" aria-hidden="true"></i>
        {{ 'shared.calendar.subscribe' | t }}
      </a>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem 0.9rem;
    }
    .feed {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      color: var(--fs-primary-strong);
      font-size: 0.88rem;
      font-weight: 650;
      text-decoration: none;
    }
    .feed:hover {
      text-decoration: underline;
    }
  `,
})
export class CalendarExportButtonComponent {
  private readonly document = inject(DOCUMENT);

  readonly ics = input.required<() => Observable<string>>();
  readonly fileName = input('foodsharing.ics');
  readonly label = input<string | undefined>(undefined);
  /** https feed URL (e.g. a guide's iCal feed); shown as a `webcal:` subscription link. */
  readonly feedUrl = input<string | null | undefined>(undefined);
  readonly small = input(false);
  readonly testId = input('shared.calendar');

  protected readonly download = runMutation(() => this.ics()(), {
    onSuccess: (text) => downloadIcs(this.document, text, this.fileName()),
  });

  private readonly sanitizer = inject(DomSanitizer);

  /** `webcal:` is not on Angular's URL allow-list; only https feed URLs from our own API are turned into it. */
  protected readonly webcalUrl = computed<SafeUrl | null>(() => {
    const url = this.feedUrl();
    return url && /^https?:\/\//.test(url)
      ? this.sanitizer.bypassSecurityTrustUrl(url.replace(/^https?:/, 'webcal:'))
      : null;
  });
}
