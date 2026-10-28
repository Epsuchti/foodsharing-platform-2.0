import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { SelectButton } from '@openng/optimus-ui/selectbutton';

import { Bell, BellPage, BellsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { BellStore } from '../bells.resource';
import { BellItemComponent } from '../components/bell-item.component';

export const BELLS_PAGE_SIZE = 20;

/** Appends a further page, skipping bells already shown (a new bell shifts the paging by one). */
export function mergeBellPages(shown: readonly Bell[], next: readonly Bell[]): Bell[] {
  const ids = new Set(shown.map((bell) => bell.id));
  return [...shown, ...next.filter((bell) => !ids.has(bell.id))];
}

/**
 * `/bells` (N3): all bells, newest first, filter all/unread, "load more" paging, delete one, delete all read, mark
 * all read. Clicking a bell marks it read and opens its link.
 */
@Component({
  selector: 'fs-bells-page',
  imports: [...DATA_STATE_IMPORTS, BellItemComponent, Button, FormsModule, PageHeaderComponent, SelectButton, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'bell.ui.page.title' | t" [subtitle]="'bell.ui.page.subtitle' | t" icon="pi pi-bell">
      <p-button
        [label]="'bell.ui.markAllRead' | t"
        icon="pi pi-check-square"
        size="small"
        [outlined]="true"
        [disabled]="!store.unreadCount()"
        [loading]="markAll.busy()"
        (onClick)="markAll.run()"
        data-testid="bells.page.markAllRead"
      />
      <p-button
        [label]="'bell.ui.page.deleteRead' | t"
        icon="pi pi-trash"
        size="small"
        severity="danger"
        [outlined]="true"
        [loading]="deleteRead.busy()"
        (onClick)="deleteRead.run()"
        data-testid="bells.page.deleteRead"
      />
    </fs-page-header>

    <div class="filters">
      <span id="bells-filter" class="fs-visually-hidden">{{ 'bell.ui.page.filter' | t }}</span>
      <p-selectbutton
        [options]="filters()"
        optionLabel="label"
        optionValue="value"
        [allowEmpty]="false"
        [ngModel]="unreadOnly()"
        (ngModelChange)="unreadOnly.set($event)"
        ariaLabelledBy="bells-filter"
        data-testid="bells.page.filter"
      />
    </div>

    <fs-data-state
      [state]="bells"
      skeleton="list"
      [skeletonRows]="6"
      emptyIcon="pi pi-bell-slash"
      [emptyTitle]="(unreadOnly() ? 'bell.ui.page.emptyUnread' : 'bell.ui.empty') | t"
      data-testid="bells.page.state"
    >
      <ng-template [fsData]="bells">
        <ul class="list fs-card">
          @for (bell of shown(); track bell.id) {
            <li>
              <fs-bell-item
                [bell]="bell"
                [deletable]="true"
                [busy]="remove.busy()"
                (open)="open($event)"
                (remove)="remove.run($event)"
                data-testid="bells.page.item"
                testId="bells.page.item"
              />
            </li>
          }
        </ul>
        @if (hasMore()) {
          <div class="more">
            <p-button
              [label]="'bell.ui.page.loadMore' | t"
              icon="pi pi-angle-down"
              [text]="true"
              [loading]="bells.status() === 'refreshing'"
              (onClick)="page.set(page() + 1)"
              data-testid="bells.page.loadMore"
            />
          </div>
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
      gap: 0.125rem;
      margin: 0;
      padding: 0.5rem;
      list-style: none;
    }
    .more {
      display: flex;
      justify-content: center;
    }
  `,
})
export class BellsPageComponent {
  private readonly api = inject(BellsService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(BellStore);

  protected readonly unreadOnly = signal(false);
  protected readonly page = signal(0);
  /** Bells of all pages loaded so far. */
  protected readonly shown = signal<Bell[]>([]);

  protected readonly filters = computed(() => {
    this.i18n.language();
    return [
      { value: false, label: this.i18n.t('bell.ui.page.all') },
      { value: true, label: this.i18n.t('bell.ui.page.unreadOnly') },
    ];
  });

  protected readonly bells = loadResource<BellPage, { unreadOnly: boolean; page: number; version: number }>({
    params: () => ({ unreadOnly: this.unreadOnly(), page: this.page(), version: this.store.version() }),
    loader: ({ unreadOnly, page }) => this.api.listBells({ unreadOnly, page, size: BELLS_PAGE_SIZE }),
    isEmpty: (result) => result.items.length === 0 && result.page.page === 0,
  });

  protected readonly hasMore = computed(() => {
    const result = this.bells.data();
    return !!result && result.page.page + 1 < result.page.totalPages;
  });

  constructor() {
    // a filter change or a change elsewhere (realtime, panel) starts again at the first page
    effect(() => {
      this.unreadOnly();
      this.store.version();
      untracked(() => {
        this.page.set(0);
        this.shown.set([]);
      });
    });
    effect(() => {
      const result = this.bells.data();
      if (!result) {
        return;
      }
      untracked(() => this.shown.set(result.page.page === 0 ? [...result.items] : mergeBellPages(this.shown(), result.items)));
    });
  }

  protected readonly markAll = runMutation(() => this.api.markAllBellsRead(), {
    successToastKey: 'bell.ui.markedAllRead',
    onSuccess: () => this.store.changed(),
  });

  protected readonly deleteRead = runMutation(() => this.api.deleteBells({ readOnly: true }), {
    successToastKey: 'bell.ui.page.deletedRead',
    onSuccess: () => this.store.changed(),
  });

  protected readonly remove = runMutation((bell: Bell) => this.api.deleteBell({ bellId: bell.id }), {
    successToastKey: 'bell.ui.deleted',
    onSuccess: () => this.store.changed(),
  });

  private readonly markRead = runMutation((bell: Bell) => this.api.markBellsRead({ bellIds: { ids: [bell.id] } }), {
    errorToast: false,
    onError: () => this.store.refresh(),
    onSuccess: () => this.store.changed(),
  });

  protected open(bell: Bell): void {
    if (!bell.readAt) {
      this.store.markedRead([bell]);
      void this.markRead.run(bell);
    }
    if (bell.link) {
      void this.router.navigateByUrl(bell.link);
    }
  }
}
