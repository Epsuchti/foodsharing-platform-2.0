import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { PublicContentService, ReleaseNote } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { hasUnseenReleaseNote } from '../content.util';
import { ReleaseNotesSeenService } from '../release-notes-seen.service';

/**
 * "What's new" indicator (C4) inside the header's release-notes link: a dot while the newest release note differs from
 * `localStorage['fs.releaseNotesSeen']`. The header loads it after the first page (`@defer`); it is positioned
 * absolutely, so it never shifts the header. Failing to load shows no dot.
 */
@Component({
  selector: 'fs-whats-new-dot',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (unseen()) {
      <span class="dot" data-testid="content.releaseNotes.dot"></span>
      <span class="fs-visually-hidden">{{ 'content.releaseNotes.unseen' | t }}</span>
    }
  `,
  styles: `
    :host {
      position: absolute;
      top: 0.4rem;
      right: 0.4rem;
      pointer-events: none;
    }
    .dot {
      display: block;
      width: 0.6rem;
      height: 0.6rem;
      border: 2px solid var(--fs-surface);
      border-radius: 50%;
      background: var(--fs-danger);
    }
  `,
})
export class WhatsNewDotComponent {
  private readonly api = inject(PublicContentService);
  private readonly seen = inject(ReleaseNotesSeenService);

  private readonly notes = loadResource<ReleaseNote[]>({
    loader: () => this.api.listReleaseNotes(),
  });

  /** Rendered by the header before the rest of the catalog (content.*) may have arrived: wait for it. */
  private readonly catalogReady = signal(false);

  protected readonly unseen = computed(() =>
    this.catalogReady() && hasUnseenReleaseNote(this.notes.data()?.[0]?.version, this.seen.seen()),
  );

  constructor() {
    void inject(I18nService)
      .ensureComplete()
      .then(() => this.catalogReady.set(true));
  }
}
