import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { BlogPost, PublicContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/** /blog/:id (C2, public): a published post with picture, author, region and Markdown body. */
@Component({
  selector: 'fs-blog-post-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    MarkdownComponent,
    PageHeaderComponent,
    RouterLink,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="post" skeleton="detail" backLink="/blog" data-testid="blog.post.state">
      <ng-template [fsData]="post" let-p>
        <fs-page-header
          [title]="p.title"
          [eyebrow]="p.region?.name ?? ('blog.list.global' | t)"
          backLink="/blog"
          [backLabel]="'blog.post.back' | t"
        >
          @if (p.mayEdit) {
            <p-button
              [label]="'blog.post.edit' | t"
              icon="pi pi-pencil"
              severity="secondary"
              [outlined]="true"
              [routerLink]="['/blog', p.id, 'edit']"
              data-testid="blog.post.edit"
            />
          }
        </fs-page-header>
        <article class="fs-card post" [attr.data-id]="p.id" data-testid="blog.post.article">
          @if (p.pictureUrl) {
            <img class="post__picture" [src]="p.pictureUrl" alt="" width="1280" height="720" data-testid="blog.post.picture" />
          }
          <div class="post__meta">
            @if (p.author; as a) {
              <fs-user-chip
                [name]="a.displayName"
                [avatarUrl]="a.avatarUrl"
                [userId]="a.id"
                [subtitle]="p.publishedAt ? (p.publishedAt | fsDate: 'date') : undefined"
                data-testid="blog.post.author"
              />
            } @else if (p.publishedAt) {
              <time [attr.datetime]="p.publishedAt">{{ p.publishedAt | fsDate: 'date' }}</time>
            }
          </div>
          @if (p.teaser) {
            <p class="post__teaser" data-testid="blog.post.teaser">{{ p.teaser }}</p>
          }
          <fs-markdown [markdown]="p.body" data-testid="blog.post.body" />
        </article>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .post {
      display: grid;
      gap: 1rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2.25rem);
    }
    .post__picture {
      width: 100%;
      height: auto;
      max-height: 26rem;
      object-fit: cover;
      border-radius: var(--fs-radius-md);
    }
    .post__meta {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .post__teaser {
      margin: 0;
      font-size: 1.08rem;
      font-weight: 550;
      line-height: 1.5;
    }
  `,
})
export class BlogPostPageComponent {
  private readonly api = inject(PublicContentService);

  /** Route parameter. */
  readonly id = input.required<string>();

  protected readonly post = loadResource<BlogPost, string>({
    params: () => this.id(),
    loader: (blogPostId) => this.api.getPublicBlogPost({ blogPostId }),
  });
}
