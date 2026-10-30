import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { Router, RouterLink, UrlTree } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { ActivityFilters, ActivityItem, ActivityPage, ActivityService, ActivityType, UsersService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { ACTIVITY_PAGE_SIZE, ACTIVITY_TYPES, ACTIVITY_TYPE_ICON, mergeActivityPages, toggleExcluded } from '../dashboard.util';

interface FeedEntry {
  item: ActivityItem;
  link: UrlTree;
  icon: string;
}

/**
 * Dashboard activity feed (A19): what happened in my forums, store walls, mailboxes, events, food share points and
 * on my buddies' walls (`getActivityFeed(page, size)`, newest first, "load more"). The filter chips switch types
 * off and on; the choice is stored on the server (`getActivityFilters` / `updateActivityFilters`).
 */
@Component({
  selector: 'fs-activity-feed-card',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, RouterLink, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="fs-card card" data-testid="dashboard.activity" [attr.aria-labelledby]="'dashboard-activity-title'">
      <h2 class="title" id="dashboard-activity-title"><i class="pi pi-bolt" aria-hidden="true"></i>{{ 'dashboard.activity.title' | t }}</h2>
      <fs-data-state [state]="filters" skeleton="list" [skeletonRows]="1" [compact]="true" data-testid="dashboard.activity.filtersState">
        <ng-template [fsData]="filters" let-current>
          <div class="chips" role="group" [attr.aria-label]="'dashboard.activity.filterLabel' | t" data-testid="dashboard.activity.filters">
            @for (type of types; track type) {
              @let on = !current.excluded.includes(type);
              <button
                type="button"
                class="chip"
                [class.chip--on]="on"
                [attr.aria-pressed]="on"
                [disabled]="saveFilters.busy()"
                (click)="saveFilters.run(type)"
                [attr.data-testid]="'dashboard.activity.filter.' + type"
              >
                <i [class]="icons[type]" aria-hidden="true"></i>{{ 'dashboard.activity.type.' + type | t }}
              </button>
            }
          </div>
        </ng-template>
      </fs-data-state>
      <fs-data-state
        [state]="feed"
        skeleton="list"
        [skeletonRows]="3"
        [emptyTitle]="'dashboard.activity.empty' | t"
        [emptyText]="'dashboard.activity.emptyHint' | t"
        emptyIcon="pi pi-bolt"
        data-testid="dashboard.activity.state"
      >
        <ng-template [fsData]="feed">
          <ul class="list" data-testid="dashboard.activity.list">
            @for (entry of entries(); track $index) {
              <li class="item" [attr.data-type]="entry.item.type" data-testid="dashboard.activity.item">
                <span class="item__icon" aria-hidden="true"><i [class]="entry.icon"></i></span>
                <div class="item__body">
                  <a class="item__title" [routerLink]="entry.link" data-testid="dashboard.activity.link">{{ entry.item.title }}</a>
                  @if (entry.item.excerpt) {
                    <p class="item__excerpt">{{ entry.item.excerpt }}</p>
                  }
                  <p class="item__meta">
                    <span class="item__type">{{ 'dashboard.activity.type.' + entry.item.type | t }}</span>
                    <span>{{ entry.item.source.name }}</span>
                    @if (entry.item.author; as author) {
                      <span class="item__author">
                        <fs-user-avatar [name]="author.displayName" [avatarUrl]="author.avatarUrl" [role]="author.role" [colorSeed]="author.id" size="xs" />
                        {{ author.displayName }}
                      </span>
                    }
                    <time [attr.datetime]="entry.item.at">{{ entry.item.at | fsDate: 'relative' }}</time>
                  </p>
                </div>
              </li>
            }
          </ul>
          @if (hasMore()) {
            <div class="more">
              <p-button
                [label]="'dashboard.activity.loadMore' | t"
                icon="pi pi-angle-down"
                [text]="true"
                [loading]="feed.status() === 'refreshing'"
                (onClick)="page.set(page() + 1)"
                data-testid="dashboard.activity.loadMore"
              />
            </div>
          }
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styles: `
    .card {
      display: grid;
      gap: 0.85rem;
      padding: 1.1rem 1.2rem;
    }
    .title {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      font-size: 1.05rem;
    }
    .chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      min-height: 2rem;
      padding: 0.25rem 0.7rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      background: var(--fs-surface);
      color: var(--fs-text-muted);
      font: inherit;
      font-size: 0.8rem;
      font-weight: 650;
      cursor: pointer;
    }
    .chip--on {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .chip:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 2px;
    }
    .chip:disabled {
      cursor: progress;
    }
    /* touch: 44px chips (design review M7 R1 #19: 32px) */
    @media (pointer: coarse) {
      .chips {
        gap: 0.45rem;
      }
      .chip {
        min-height: 2.75rem;
        padding-inline: 0.85rem;
      }
    }
    .list {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .item {
      display: grid;
      grid-template-columns: 2.25rem minmax(0, 1fr);
      gap: 0.75rem;
      padding: 0.75rem 0;
    }
    .item + .item {
      border-top: 1px solid var(--fs-border);
    }
    .item__icon {
      display: grid;
      width: 2.25rem;
      height: 2.25rem;
      place-items: center;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .item__body {
      display: grid;
      gap: 0.2rem;
      min-width: 0;
    }
    .item__title {
      color: var(--fs-text);
      font-weight: 700;
      overflow-wrap: anywhere;
    }
    .item__excerpt {
      display: -webkit-box;
      overflow: hidden;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      line-clamp: 2;
    }
    .item__meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.2rem 0.75rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
    .item__type {
      color: var(--fs-primary-strong);
      font-weight: 700;
    }
    .item__author {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
    }
    .more {
      display: flex;
      justify-content: center;
    }
  `,
})
export class ActivityFeedCardComponent {
  private readonly activityApi = inject(ActivityService);
  private readonly usersApi = inject(UsersService);
  private readonly router = inject(Router);

  protected readonly types = ACTIVITY_TYPES;
  protected readonly icons = ACTIVITY_TYPE_ICON;
  protected readonly page = signal(0);
  /** Bumped after a filter change: the feed starts again at the first page. */
  private readonly version = signal(0);
  private readonly shown = signal<ActivityItem[]>([]);

  protected readonly filters = loadResource<ActivityFilters>({
    loader: () => this.usersApi.getActivityFilters(),
    isEmpty: () => false,
  });

  protected readonly feed = loadResource<ActivityPage, { page: number; version: number }>({
    params: () => ({ page: this.page(), version: this.version() }),
    loader: ({ page }) => this.activityApi.getActivityFeed({ page, size: ACTIVITY_PAGE_SIZE }),
    isEmpty: (result) => result.items.length === 0 && result.page.page === 0,
  });

  protected readonly hasMore = computed(() => {
    const result = this.feed.data();
    return !!result && result.page.page + 1 < result.page.totalPages;
  });

  protected readonly entries = computed<FeedEntry[]>(() =>
    this.shown().map((item) => ({ item, link: this.router.parseUrl(item.link), icon: ACTIVITY_TYPE_ICON[item.type] })),
  );

  protected readonly saveFilters = runMutation(
    (type: ActivityType) =>
      this.usersApi.updateActivityFilters({
        activityFilters: { excluded: toggleExcluded(this.filters.data()?.excluded ?? [], type) },
      }),
    {
      onSuccess: (result) => {
        this.filters.set(result);
        this.page.set(0);
        this.version.update((value) => value + 1);
      },
    },
  );

  constructor() {
    effect(() => {
      const result = this.feed.data();
      if (!result) {
        return;
      }
      untracked(() => this.shown.set(result.page.page === 0 ? [...result.items] : mergeActivityPages(this.shown(), result.items)));
    });
  }
}
