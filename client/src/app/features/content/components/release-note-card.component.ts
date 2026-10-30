import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ReleaseNote } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';

/** One release note (C4): version, date, "new" badge, title and Markdown text. */
@Component({
  selector: 'fs-release-note-card',
  imports: [DateTimePipe, MarkdownComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-card',
    '[attr.data-version]': 'note().version',
    '[attr.data-new]': 'isNew()',
    'data-testid': 'content.releaseNotes.note',
  },
  template: `
    @let n = note();
    <header class="head">
      <span class="version">{{ 'content.releaseNotes.version' | t: { version: n.version } }}</span>
      <time class="date" [attr.datetime]="n.releasedAt">{{
        n.releasedAt | fsDate: 'dateLong'
      }}</time>
      @if (isNew()) {
        <span class="new" data-testid="content.releaseNotes.new">{{
          'content.releaseNotes.new' | t
        }}</span>
      }
    </header>
    <h2 class="title">{{ n.title }}</h2>
    <fs-markdown [markdown]="n.body" />
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.5rem;
      padding: 1.1rem 1.25rem;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem 0.75rem;
      font-size: 0.85rem;
    }
    .version,
    .new {
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      font-weight: 700;
    }
    .version {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-variant-numeric: tabular-nums;
    }
    .date {
      color: var(--fs-text-muted);
    }
    .new {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .title {
      margin: 0;
      font-size: 1.15rem;
    }
  `,
})
export class ReleaseNoteCardComponent {
  readonly note = input.required<ReleaseNote>();
  readonly isNew = input(false);
}
