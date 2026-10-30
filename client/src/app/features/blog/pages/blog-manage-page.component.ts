import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';

import { BlogPostPage, ContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { BlogStatusTagComponent } from '../components/blog-status-tag.component';

const PAGE_SIZE = 20;

/**
 * /blog/manage (C2): the posts the user may edit — every post for editors (ORGA, PR group admins), the posts of their
 * regions and their own drafts for ambassadors — with status (published / awaiting check / draft). Everybody else
 * gets the forbidden state (403 from `listBlogPosts`).
 */
@Component({
  selector: 'fs-blog-manage-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BlogStatusTagComponent,
    Button,
    DateTimePipe,
    PageHeaderComponent,
    Paginator,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'blog.manage.title' | t"
      [subtitle]="'blog.manage.subtitle' | t"
      icon="pi pi-pencil"
      backLink="/blog"
      [backLabel]="'blog.manage.back' | t"
    >
      <p-button
        [label]="'blog.manage.new' | t"
        icon="pi pi-plus"
        routerLink="/blog/new"
        data-testid="blog.manage.new"
      />
    </fs-page-header>

    <fs-data-state
      [state]="posts"
      skeleton="list"
      emptyIcon="pi pi-book"
      [emptyTitle]="'blog.manage.empty' | t"
      [emptyText]="'blog.manage.emptyText' | t"
      [askWho]="'blog.manage.askWho' | t"
      backLink="/blog"
      data-testid="blog.manage.state"
    >
      <ng-template [fsData]="posts" let-result>
        <ul class="fs-card list">
          @for (post of result.items; track post.id) {
            <li class="row" [attr.data-id]="post.id" data-testid="blog.manage.post">
              <span class="row__main">
                <a class="row__title" [routerLink]="['/blog', post.id, 'edit']" data-testid="blog.manage.edit">{{
                  post.title
                }}</a>
                <span class="row__meta">
                  {{ post.region?.name ?? ('blog.list.global' | t) }}
                  @if (post.author) {
                    · {{ post.author.displayName }}
                  }
                  @if (post.publishedAt) {
                    · {{ post.publishedAt | fsDate: 'date' }}
                  }
                </span>
              </span>
              <fs-blog-status-tag
                [published]="post.published"
                [awaitingCheck]="post.awaitingCheck"
                testId="blog.manage.status"
              />
            </li>
          }
        </ul>
        @if (result.page.totalPages > 1) {
          <p-paginator
            [first]="result.page.page * result.page.size"
            [rows]="result.page.size"
            [totalRecords]="result.page.totalItems"
            [pageLinkSize]="3"
            (onPageChange)="onPage($event)"
            data-testid="blog.manage.paginator"
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
    .list {
      display: grid;
      margin: 0;
      padding: 0.35rem;
      list-style: none;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem 1rem;
      padding: 0.7rem 0.85rem;
    }
    .row + .row {
      border-top: 1px solid var(--fs-border);
    }
    .row__main {
      display: grid;
      flex: 1 1 16rem;
      min-width: 0;
      gap: 0.1rem;
    }
    .row__title {
      overflow: hidden;
      color: inherit;
      font-weight: 650;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .row__meta {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
  `,
})
export class BlogManagePageComponent {
  private readonly api = inject(ContentService);
  private readonly page = signal(0);

  protected readonly posts = loadResource<BlogPostPage, number>({
    params: () => this.page(),
    loader: (page) => this.api.listBlogPosts({ page, size: PAGE_SIZE }),
  });

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }
}
