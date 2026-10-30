import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';

import { BlogPostPage, PublicContentService, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { mayWriteBlog } from '../blog.util';
import { BlogPostCardComponent } from '../components/blog-post-card.component';

const PAGE_SIZE = 12;

/**
 * /blog (C2, public, BLOG toggle): published posts, newest first, 12 per page (`?page=` zero-based); `?region=`
 * narrows the list to one region (the region chips of the cards link there). Editors and ambassadors get a link to
 * the management page.
 */
@Component({
  selector: 'fs-blog-list-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BlogPostCardComponent,
    Button,
    PageHeaderComponent,
    Paginator,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'blog.list.title' | t"
      [subtitle]="'blog.list.subtitle' | t"
      icon="pi pi-book"
    >
      @if (mayWrite()) {
        <p-button
          [label]="'blog.list.manage' | t"
          icon="pi pi-pencil"
          severity="secondary"
          [outlined]="true"
          routerLink="/blog/manage"
          data-testid="blog.list.manage"
        />
      }
    </fs-page-header>

    @if (regionName(); as name) {
      <p class="filter" data-testid="blog.list.filter">
        <i class="pi pi-filter" aria-hidden="true"></i>
        <span>{{ 'blog.list.filtered' | t: { region: name } }}</span>
        <a routerLink="/blog" data-testid="blog.list.clearFilter">{{ 'blog.list.clearFilter' | t }}</a>
      </p>
    }

    <fs-data-state
      [state]="posts"
      skeleton="card"
      emptyIcon="pi pi-book"
      [emptyTitle]="'blog.list.empty' | t"
      [emptyText]="'blog.list.emptyText' | t"
      backLink="/"
      data-testid="blog.list.state"
    >
      <ng-template [fsData]="posts" let-result>
        <ul class="grid" data-testid="blog.list.grid">
          @for (post of result.items; track post.id) {
            <li><fs-blog-post-card [post]="post" /></li>
          }
        </ul>
        @if (result.page.totalPages > 1) {
          <p-paginator
            [first]="result.page.page * result.page.size"
            [rows]="result.page.size"
            [totalRecords]="result.page.totalItems"
            [pageLinkSize]="3"
            (onPageChange)="onPage($event)"
            data-testid="blog.list.paginator"
          />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr));
      gap: 1.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .filter {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      color: var(--fs-text-muted);
    }
    p-paginator {
      margin-top: 1rem;
    }
  `,
})
export class BlogListPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  /** Query parameters. */
  readonly region = input<string | undefined>(undefined);
  readonly page = input<string | undefined>(undefined);

  private readonly pageIndex = computed(() => Math.max(0, Number(this.page() ?? 0) || 0));

  protected readonly posts = loadResource<BlogPostPage, { regionId?: string; page: number }>({
    params: () => ({ regionId: this.region() || undefined, page: this.pageIndex() }),
    loader: (params) => this.api.listPublicBlogPosts({ ...params, size: PAGE_SIZE }),
  });

  /** Name of the filter region (from the loaded posts; the filter link stays without a name on an empty page). */
  protected readonly regionName = computed(() => {
    const id = this.region();
    if (!id) {
      return null;
    }
    const match = this.posts.data()?.items.find((post) => post.region?.id === id);
    return match?.region?.name ?? null;
  });

  protected readonly mayWrite = computed(
    () =>
      this.session.isAuthenticated() &&
      mayWriteBlog(this.session.session(), this.session.hasRole(Role.Orga)),
  );

  protected onPage(event: PaginatorState): void {
    void this.router.navigate([], {
      queryParams: { page: event.page || null },
      queryParamsHandling: 'merge',
    });
  }
}
