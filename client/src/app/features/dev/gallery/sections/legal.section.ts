import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { Language, LegalDocument, LegalDocumentKind } from '../../../../api';
import { LegalDocumentViewComponent } from '../../../legal/components/legal-document-view.component';

/** Legal document view (Ve1): with key points, and the German fallback note. */
@Component({
  selector: 'fs-gallery-legal-section',
  imports: [LegalDocumentViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-legal">Legal</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.legalDocument">
        <header class="specimen__head"><code>fs-legal-document-view</code></header>
        <fs-legal-document-view [document]="privacy" />
      </section>
      <section class="specimen" data-testid="gallery.legalDocumentFallback">
        <header class="specimen__head"><code>fs-legal-document-view</code> fallback</header>
        <fs-legal-document-view [document]="fallback" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class LegalGallerySection {
  protected readonly privacy: LegalDocument = {
    kind: LegalDocumentKind.PrivacyPolicy,
    version: '2026-11',
    publishedAt: '2026-11-01T08:00:00Z',
    language: Language.De,
    summary: '- Wir speichern nur, was für das Retten nötig ist.\n- Deine Handynummer sieht nur dein Team.',
    body: '## 1. Verantwortliche Stelle\n\nfoodsharing Schweiz, Zürich.\n\n## 2. Welche Daten\n\nName, E-Mail, Handynummer und deine Abholungen.',
    fallback: false,
  };
  protected readonly fallback: LegalDocument = {
    kind: LegalDocumentKind.ConfidentialityNotice,
    version: '2026-01',
    publishedAt: '2026-01-01T00:00:00Z',
    language: Language.De,
    body: 'Informationen über Betriebe bleiben im Team.',
    fallback: true,
  };
}

/** Gallery sections of the legal feature (one file per module, no conflicts). */
export const LEGAL_GALLERY_SECTIONS: readonly Type<unknown>[] = [LegalGallerySection];
