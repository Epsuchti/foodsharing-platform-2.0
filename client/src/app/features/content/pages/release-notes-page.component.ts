import {
  ChangeDetectionStrategy,
  Component,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';

import { Language, PublicContentService, ReleaseNote } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ReleaseNoteCardComponent } from '../components/release-note-card.component';
import { unseenVersions } from '../content.util';
import { ReleaseNotesSeenService } from '../release-notes-seen.service';

/**
 * /release-notes (C4, public): what is new, newest first, in the UI language (German fallback). Notes the visitor
 * has not seen are marked "new"; opening the page marks the newest note seen, which clears the header dot.
 */
@Component({
  selector: 'fs-release-notes-page',
  imports: [...DATA_STATE_IMPORTS, PageHeaderComponent, ReleaseNoteCardComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.releaseNotes.title' | t"
      [subtitle]="'content.releaseNotes.subtitle' | t"
      icon="pi pi-gift"
    />
    <fs-data-state
      [state]="notes"
      skeleton="list"
      emptyIcon="pi pi-gift"
      [emptyTitle]="'content.releaseNotes.empty' | t"
      data-testid="content.releaseNotes.state"
    >
      <ng-template [fsData]="notes" let-items>
        <ol class="timeline">
          @for (note of items; track note.id) {
            <li><fs-release-note-card [note]="note" [isNew]="unseen().has(note.version)" /></li>
          }
        </ol>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .timeline {
      display: grid;
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
  `,
})
export class ReleaseNotesPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly i18n = inject(I18nService);
  private readonly seen = inject(ReleaseNotesSeenService);

  /** Unseen versions as of opening the page (kept while the page is open, although the newest is now seen). */
  protected readonly unseen = signal<Set<string>>(new Set());

  protected readonly notes = loadResource<ReleaseNote[], string>({
    params: () => this.i18n.language(),
    loader: (lang) => this.api.listReleaseNotes({ lang: lang as Language }),
  });

  constructor() {
    let marked = false;
    effect(() => {
      const items = this.notes.data();
      if (!items || marked) {
        return;
      }
      marked = true;
      untracked(() => {
        this.unseen.set(unseenVersions(items, this.seen.seen()));
        if (items.length) {
          this.seen.markSeen(items[0].version);
        }
      });
    });
  }
}
