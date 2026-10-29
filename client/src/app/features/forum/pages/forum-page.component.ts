import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';

import { ForumFollowState, ForumSearchPage, ForumService, ForumThreadPage } from '../../../api';
import { isAmbassador } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { highlight } from '../forum.resource';

/**
 * /regions/:regionId/forum[/ambassadors] (F1, F2, F4, F5): thread list (sticky first, closed lock, "awaiting
 * activation" for moderators and the author, followed bell), search in titles and optionally posts with snippets,
 * "Follow forum" (bell for every new thread) and "New thread". Ambassadors see the ambassador forum tab and the
 * moderation log link.
 */
@Component({
  selector: 'fs-forum-page',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, InputText, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(ambassadorForum() ? 'forum.ambassadors' : 'forum.title') | t"
      [subtitle]="regionName()"
      icon="pi pi-comments"
      [backLink]="['/regions', regionId()]"
      [backLabel]="regionName() ?? ('forum.region' | t)"
    >
      <p-button
        [label]="'forum.newThread' | t"
        icon="pi pi-plus"
        [routerLink]="newLink()"
        data-testid="forum.list.new"
      />
    </fs-page-header>

    <div class="bar">
      @if (ambassador()) {
        <nav class="tabs" [attr.aria-label]="'forum.tabs' | t">
          <a class="tab" [class.tab--active]="!ambassadorForum()" [routerLink]="['/regions', regionId(), 'forum']" [attr.aria-current]="!ambassadorForum() ? 'page' : null" data-testid="forum.list.tabForum">{{ 'forum.title' | t }}</a>
          <a class="tab" [class.tab--active]="ambassadorForum()" [routerLink]="['/regions', regionId(), 'forum', 'ambassadors']" [attr.aria-current]="ambassadorForum() ? 'page' : null" data-testid="forum.list.tabAmbassadors">{{ 'forum.ambassadors' | t }}</a>
        </nav>
        <a class="log" [routerLink]="['/regions', regionId(), 'moderation-log']" data-testid="forum.list.log"><i class="pi pi-history" aria-hidden="true"></i> {{ 'forum.log.title' | t }}</a>
      }
      <span class="spacer"></span>
      @if (follow.data(); as f) {
        <p-button
          [label]="(f.following ? 'forum.unfollowForum' : 'forum.followForum') | t"
          [icon]="f.following ? 'pi pi-bell-slash' : 'pi pi-bell'"
          severity="secondary" [outlined]="true"
          size="small"
          [loading]="toggleFollow.busy()"
          (onClick)="toggleFollow.run(!f.following)"
          [attr.data-following]="f.following"
          data-testid="forum.list.follow"
        />
      }
    </div>

    <form class="search" role="search" (ngSubmit)="search()">
      <label class="sr-only" for="forum-search">{{ 'forum.search.label' | t }}</label>
      <input pInputText id="forum-search" type="search" name="q" [placeholder]="'forum.search.label' | t" [(ngModel)]="queryInput" data-testid="forum.search.input" />
      <span class="check">
        <p-checkbox name="body" [binary]="true" [(ngModel)]="bodyInput" inputId="forum-search-body" data-testid="forum.search.body" />
        <label for="forum-search-body">{{ 'forum.search.body' | t }}</label>
      </span>
      <p-button type="submit" icon="pi pi-search" [label]="'forum.search.submit' | t" severity="secondary" [outlined]="true" data-testid="forum.search.submit" />
      @if (query()) {
        <p-button icon="pi pi-times" [text]="true" [ariaLabel]="'forum.search.clear' | t" (onClick)="clear()" data-testid="forum.search.clear" />
      }
    </form>

    @if (query()) {
      <fs-data-state [state]="results" skeleton="list" [emptyTitle]="'forum.search.empty' | t" emptyIcon="pi pi-search" data-testid="forum.search.state">
        <ng-template [fsData]="results" let-page>
          <ul class="threads fs-card">
            @for (hit of page.items; track hit.thread.id) {
              <li>
                <a class="thread" [routerLink]="['/forum/threads', hit.thread.id]" data-testid="forum.search.hit" [attr.data-thread-id]="hit.thread.id">
                  <span class="thread__title">{{ hit.thread.title }}</span>
                  @if (hit.snippet) {
                    <span class="snippet" data-testid="forum.search.snippet">
                      @for (part of parts(hit.snippet); track $index) {
                        @if (part.match) {<mark>{{ part.text }}</mark>} @else {{{ part.text }}}
                      }
                    </span>
                  }
                </a>
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    } @else {
      <fs-data-state
        [state]="threads"
        skeleton="list"
        [emptyTitle]="'forum.emptyTitle' | t"
        [emptyText]="'forum.emptyText' | t"
        emptyIcon="pi pi-comments"
        data-testid="forum.list.state"
      >
        <ng-template [fsData]="threads" let-page>
          <ul class="threads fs-card">
            @for (t of page.items; track t.id) {
              <li>
                <a class="thread" [class.thread--inactive]="!t.active" [routerLink]="['/forum/threads', t.id]" data-testid="forum.list.thread" [attr.data-thread-id]="t.id" [attr.data-title]="t.title">
                  <span class="thread__title">
                    @if (t.sticky) {<i class="pi pi-thumbtack" [attr.aria-label]="'forum.sticky' | t" role="img" data-testid="forum.list.sticky"></i>}
                    @if (t.status === 'CLOSED') {<i class="pi pi-lock" [attr.aria-label]="'forum.closed' | t" role="img" data-testid="forum.list.closed"></i>}
                    {{ t.title }}
                    @if (!t.active) {<span class="badge badge--warn" data-testid="forum.list.inactive">{{ 'forum.inactive' | t }}</span>}
                    @if (t.following.bell || t.following.mail) {<i class="pi pi-bell muted" [attr.aria-label]="'forum.following' | t" role="img"></i>}
                  </span>
                  <span class="thread__meta">
                    {{ 'forum.posts' | t: { count: t.postCount } }}
                    @if (t.lastPost; as last) {
                      · {{ last.author?.displayName }} · <time [attr.datetime]="last.at">{{ last.at | fsDate: 'relative' }}</time>
                    }
                  </span>
                </a>
              </li>
            }
          </ul>
          @if (page.page.totalItems > page.items.length) {
            <p-button class="more" [label]="'forum.more' | t" [text]="true" (onClick)="size.set(size() + 50)" data-testid="forum.list.more" />
          }
        </ng-template>
      </fs-data-state>
    }
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 60rem); margin: 0 auto; }
    .bar, .search { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .spacer { flex: 1; }
    .tabs { display: flex; gap: 0.35rem; }
    .tab, .log {
      display: inline-flex; align-items: center; gap: 0.35rem; min-height: 2.5rem; padding: 0.35rem 0.8rem;
      border-radius: 999px; border: 1px solid var(--fs-border); color: inherit; text-decoration: none; font-weight: 600;
    }
    .tab--active { background: var(--fs-primary-soft); border-color: var(--fs-primary); color: var(--fs-primary-strong); }
    .search input { flex: 1 1 14rem; }
    .check { display: inline-flex; align-items: center; gap: 0.4rem; min-height: 2.5rem; }
    .threads { list-style: none; margin: 0; padding: 0; }
    .threads li + li { border-top: 1px solid var(--fs-border); }
    .thread { display: grid; gap: 0.2rem; padding: 0.75rem 1rem; color: inherit; text-decoration: none; }
    .thread:hover, .thread:focus-visible { background: var(--fs-surface-muted); }
    .thread__title { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; font-weight: 600; }
    .thread__meta, .muted, .snippet { color: var(--fs-text-muted); font-size: 0.9rem; }
    mark { background: var(--fs-warning-soft); color: inherit; }
    .badge { display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.1rem 0.5rem; border-radius: 999px; border: 1px solid var(--fs-border); background: var(--fs-surface-muted); color: var(--fs-text); font-size: 0.8rem; font-weight: 600; }
    .badge--warn { border-color: var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .more { justify-self: center; }
  `,
})
export class ForumPageComponent {
  private readonly api = inject(ForumService);
  private readonly session = inject(SessionService);

  readonly regionId = input.required<string>();
  readonly ambassadorForum = input(false, { transform: (value: unknown) => value === true });

  protected readonly ambassador = computed(() => isAmbassador(this.session.session(), this.regionId()));
  protected readonly regionName = computed(
    () => (this.session.session().units ?? []).find((unit) => unit.id === this.regionId())?.name,
  );
  protected readonly newLink = computed(() =>
    this.ambassadorForum() ? ['/regions', this.regionId(), 'forum', 'ambassadors', 'new'] : ['/regions', this.regionId(), 'forum', 'new'],
  );

  protected queryInput = '';
  protected bodyInput = false;
  protected readonly query = signal('');
  private readonly searchBody = signal(false);
  protected readonly size = signal(50);

  protected readonly threads = loadResource<ForumThreadPage, { regionId: string; ambassadorForum: boolean; size: number }>({
    params: () => ({ regionId: this.regionId(), ambassadorForum: this.ambassadorForum(), size: this.size() }),
    loader: (p) => this.api.listForumThreads(p),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly results = loadResource<ForumSearchPage, { regionId: string; q: string; searchBody: boolean; ambassadorForum: boolean } | undefined>({
    params: () =>
      this.query()
        ? { regionId: this.regionId(), q: this.query(), searchBody: this.searchBody(), ambassadorForum: this.ambassadorForum() }
        : undefined,
    loader: (p) => this.api.searchRegionForum({ ...p!, size: 50 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly follow = loadResource<ForumFollowState, { regionId: string; ambassadorForum: boolean }>({
    params: () => ({ regionId: this.regionId(), ambassadorForum: this.ambassadorForum() }),
    loader: (p) => this.api.getForumFollow(p),
    isEmpty: () => false,
  });

  protected readonly toggleFollow = runMutation(
    (following: boolean) =>
      this.api.setForumFollow({ regionId: this.regionId(), ambassadorForum: this.ambassadorForum(), forumFollowState: { following } }),
    { successToastKey: 'forum.followSaved', onSuccess: () => this.follow.reload() },
  );

  protected search(): void {
    this.searchBody.set(this.bodyInput);
    this.query.set(this.queryInput.trim());
  }

  protected clear(): void {
    this.queryInput = '';
    this.query.set('');
  }

  protected parts(snippet: string) {
    return highlight(snippet, this.query());
  }
}
