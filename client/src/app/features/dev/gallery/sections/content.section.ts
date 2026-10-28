import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { LearningChapter } from '../../../../api';
import { LearningChapterItemComponent } from '../../../content/components/learning-chapter-item.component';

/** Learning chapter items (C1) in their three states: read, up next, open. */
@Component({
  selector: 'fs-gallery-content-section',
  imports: [LearningChapterItemComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-content">Content</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.learningChapters">
        <header class="specimen__head"><code>fs-learning-chapter-item</code></header>
        <div style="display: grid; gap: 0.75rem">
          <fs-learning-chapter-item [chapter]="chapters[0]" [position]="1" state="read" />
          <fs-learning-chapter-item [chapter]="chapters[1]" [position]="2" state="next" />
          <fs-learning-chapter-item [chapter]="chapters[2]" [position]="3" state="open" />
        </div>
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class ContentGallerySection {
  protected readonly chapters: LearningChapter[] = [
    { key: 'rules', title: 'Die Regeln', summary: 'Was wir versprechen und warum.', estimatedMinutes: 2, sortOrder: 1 },
    { key: 'hygiene', title: 'Hygiene', summary: 'Kühlkette, Haltbarkeit, saubere Hände.', estimatedMinutes: 2, sortOrder: 2 },
    { key: 'how-a-pickup-works', title: 'So läuft eine Abholung', estimatedMinutes: 3, sortOrder: 3 },
  ];
}

/** Gallery sections of the content feature (one file per module, no conflicts). */
export const CONTENT_GALLERY_SECTIONS: readonly Type<unknown>[] = [ContentGallerySection];
