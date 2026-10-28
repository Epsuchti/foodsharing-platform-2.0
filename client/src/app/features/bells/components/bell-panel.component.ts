import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, output } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Bell, BellPage, BellsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { BellStore } from '../bells.resource';
import { BellItemComponent } from './bell-item.component';

/** Number of bells in the header panel (N3). */
export const BELL_PANEL_SIZE = 10;

/**
 * Header bell panel (N3): the last 10 bells, "mark all read", link to `/bells`. Clicking a bell marks it read and
 * opens its link. Lazy-loaded by `fs-bell-button` on the first open; reloads whenever `BellStore.version` changes.
 */
@Component({
  selector: 'fs-bell-panel',
  imports: [...DATA_STATE_IMPORTS, BellItemComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'dialog',
    '[attr.aria-label]': 'label()',
    'data-testid': 'bell.ui.panel',
    '(document:keydown.escape)': 'closed.emit()',
    '(document:click)': 'onDocumentClick($event)',
  },
  template: `
    <header class="panel__head">
      <h2 class="panel__title">{{ 'bell.ui.panel.title' | t }}</h2>
      <button
        type="button"
        class="fs-btn fs-btn--text fs-btn--sm"
        [disabled]="!store.unreadCount() || markAll.busy()"
        (click)="markAll.run()"
        data-testid="bells.panel.markAllRead"
      >
        <i class="pi pi-check-square" aria-hidden="true"></i>{{ 'bell.ui.markAllRead' | t }}
      </button>
    </header>
    <fs-data-state
      [state]="bells"
      skeleton="list"
      [skeletonRows]="3"
      [compact]="true"
      emptyIcon="pi pi-bell-slash"
      [emptyTitle]="'bell.ui.empty' | t"
      data-testid="bells.panel.state"
    >
      <ng-template [fsData]="bells" let-page>
        <ul class="panel__list">
          @for (bell of page.items; track bell.id) {
            <li><fs-bell-item [bell]="bell" (open)="open($event)" data-testid="bells.panel.item" testId="bells.panel.item" /></li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
    <footer class="panel__foot">
      <a routerLink="/bells" class="fs-btn fs-btn--text fs-btn--sm" (click)="closed.emit()" data-testid="bells.panel.all">
        {{ 'bell.ui.panel.showAll' | t }}<i class="pi pi-arrow-right" aria-hidden="true"></i>
      </a>
    </footer>
  `,
  styles: `
    :host {
      position: absolute;
      top: calc(100% + 0.5rem);
      inset-inline-end: 0;
      z-index: 1000;
      display: grid;
      gap: 0.5rem;
      width: min(24rem, calc(100vw - 1rem));
      max-height: min(32rem, calc(100dvh - 5rem));
      padding: 0.75rem;
      overflow-y: auto;
      border: 1px solid var(--fs-border);
      border-radius: 0.75rem;
      background: var(--fs-surface);
      box-shadow: var(--fs-shadow-lg);
      text-align: start;
    }
    @media (max-width: 40rem) {
      :host {
        position: fixed;
        top: calc(var(--fs-header-h) + 0.25rem);
        inset-inline: 0.5rem;
        width: auto;
      }
    }
    .panel__head,
    .panel__foot {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .panel__foot {
      justify-content: center;
    }
    .panel__title {
      margin: 0;
      font-size: 1rem;
    }
    .panel__list {
      display: grid;
      gap: 0.125rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
  `,
})
export class BellPanelComponent {
  private readonly api = inject(BellsService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(BellStore);
  protected readonly label = computed(() => this.i18n.t('bell.ui.panel.title'));

  /** Emitted on Escape, outside click, navigation. */
  readonly closed = output<void>();

  protected readonly bells = loadResource<BellPage, number>({
    params: () => this.store.version(),
    loader: () => this.api.listBells({ size: BELL_PANEL_SIZE }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly markAll = runMutation(() => this.api.markAllBellsRead(), {
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
    this.closed.emit();
    if (bell.link) {
      void this.router.navigateByUrl(bell.link);
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    const target = event.target as Node | null;
    // the toggle button lives in the parent (fs-bell-button): it handles its own clicks
    const button = this.host.nativeElement.parentElement;
    if (target && !this.host.nativeElement.contains(target) && !button?.contains(target)) {
      this.closed.emit();
    }
  }
}
