import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { BlogPostSummary, Role, UnitType } from '../../../../api';
import { BlogPostCardComponent } from '../../../blog/components/blog-post-card.component';
import { BlogStatusTagComponent } from '../../../blog/components/blog-status-tag.component';

/** Blog (C2): post cards (region post with picture, global post without) and the three editor states. */
@Component({
  selector: 'fs-gallery-blog-section',
  imports: [BlogPostCardComponent, BlogStatusTagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-blog">Blog</h2>
    <div class="grid">
      <section class="specimen" style="grid-column: 1 / -1" data-testid="gallery.blogCards">
        <header class="specimen__head"><code>fs-blog-post-card</code> region / global</header>
        <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr)); gap: 1rem">
          <fs-blog-post-card [post]="posts[0]" />
          <fs-blog-post-card [post]="posts[1]" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.blogStatus">
        <header class="specimen__head"><code>fs-blog-status-tag</code></header>
        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem">
          <fs-blog-status-tag [published]="true" />
          <fs-blog-status-tag [published]="false" [awaitingCheck]="true" />
          <fs-blog-status-tag [published]="false" />
        </div>
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class BlogGallerySection {
  protected readonly posts: BlogPostSummary[] = [
    {
      id: 'gallery-blog-1',
      title: 'Fairteiler am Stauffacher eröffnet',
      teaser:
        'Seit dieser Woche steht ein neuer Kühlschrank am Stauffacher. Danke an alle, die beim Aufbau geholfen haben!',
      pictureUrl: '/api/uploads/00000040-0001-4000-8000-000000000401?size=lg',
      publishedAt: '2026-10-28T09:00:00Z',
      published: true,
      region: {
        id: '00000010-0001-4000-8000-000000000040',
        slug: 'zuerich-stadt',
        name: 'Zürich Stadt',
        type: UnitType.City,
      },
      author: { id: 'gallery-user', displayName: 'Bot Botschafterin', role: Role.Ambassador, verified: true },
    },
    {
      id: 'gallery-blog-2',
      title: 'Jahresbericht 2025',
      teaser: 'Wie viel Lebensmittel wir gemeinsam gerettet haben – und was 2026 kommt.',
      publishedAt: '2026-09-15T09:00:00Z',
      published: true,
    },
  ];
}

export const BLOG_GALLERY_SECTIONS: readonly Type<unknown>[] = [BlogGallerySection];
