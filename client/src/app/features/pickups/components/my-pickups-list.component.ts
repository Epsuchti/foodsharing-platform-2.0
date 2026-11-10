import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button, ButtonDirective, ButtonIcon, ButtonLabel } from '@openng/optimus-ui/button';

import { UserCleaningShift, UserPickup } from '../../../api';
import { DataState } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { mergeByTime } from '../pickups.util';

export interface MyPickups {
  pickups: UserPickup[];
  cleaning: UserCleaningShift[];
}

/** Cards shown at first and per "Show more" (docs/design/ui-polish-batch-proposal.md §3). */
export const MY_PICKUPS_PAGE = 5;

/**
 * "My pickups" section of /pickups (PU3, decision 154, owner feedback 2026-11-04): my upcoming pickups and cleaning
 * shifts, confirmed or waiting, with sign-out; the first 5, then "Show N more". The page owns the data and the
 * sign-out flows; this component renders the section (`id="mine"` for the `#mine` fragment).
 */
@Component({
  selector: 'fs-my-pickups-list',
  imports: [...DATA_STATE_IMPORTS, Button, ButtonDirective, ButtonIcon, ButtonLabel, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="section" id="mine" aria-labelledby="mine-title" data-testid="pickups.mine">
      <div class="section__head">
        <h2 class="section__title" id="mine-title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'pickups.mine.title' | t }}</h2>
        @if (entries().length > 0) {
          <span class="section__count" data-testid="pickups.mine.count">{{ entries().length }}</span>
        }
      </div>
      <fs-data-state
        [state]="state()"
        skeleton="list"
        [skeletonRows]="3"
        [emptyTitle]="'pickups.mine.empty' | t"
        [emptyText]="'pickups.mine.emptyText' | t"
        emptyIcon="pi pi-calendar"
        [emptyActionLabel]="'pickups.options.title' | t"
        emptyActionIcon="pi pi-arrow-down"
        (emptyAction)="findOptions.emit()"
        testId="pickups.mine.state"
        data-testid="pickups.mine.state"
      >
        <ng-template [fsData]="state()">
          <ul class="cards" id="mine-list" #list>
            @for (entry of visible(); track entry.key) {
              @if (entry.pickup; as pickup) {
                <li class="fs-card item" data-testid="pickups.mine.pickup" [attr.data-store]="pickup.store.name" [attr.data-confirmed]="pickup.confirmed">
                  <div class="item__main">
                    <a class="item__store" [routerLink]="['/stores', pickup.store.id, 'pickups']">{{ pickup.store.name }}</a>
                    <span class="item__when">{{ pickup.pickupAt | fsDate: 'dateTime' }}</span>
                    @if (pickup.confirmed) {
                      <span class="state state--ok" data-testid="pickups.mine.confirmed"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'pickups.mine.confirmed' | t }}</span>
                    } @else {
                      <span class="state state--wait" data-testid="pickups.mine.unconfirmed"><i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.waiting' | t }}</span>
                    }
                  </div>
                  <p-button
                    [label]="'pickups.signout.confirm' | t"
                    [ariaLabel]="'pickups.mine.signOutLabel' | t: { store: pickup.store.name, date: (pickup.pickupAt | fsDate: 'dateTime') }"
                    icon="pi pi-sign-out"
                    severity="danger"
                    [text]="true"
                    size="small"
                    (onClick)="signOut.emit(pickup)"
                    data-testid="pickups.mine.signOut"
                  />
                </li>
              } @else if (entry.shift; as shift) {
                <li class="fs-card item item--cleaning" data-testid="pickups.mine.cleaning" [attr.data-fsp]="shift.fsp.name" [attr.data-confirmed]="shift.confirmed">
                  <div class="item__main">
                    <a class="item__store" [routerLink]="['/food-share-points', shift.fsp.id, 'cleaning']">{{ shift.fsp.name }}</a>
                    <span class="kind"><i class="pi pi-sparkles" aria-hidden="true"></i>{{ 'pickups.cleaning.label' | t }}</span>
                    <span class="item__when">{{ shift.shiftAt | fsDate: 'dateTime' }}</span>
                    @if (shift.confirmed) {
                      <span class="state state--ok"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'pickups.mine.confirmed' | t }}</span>
                    } @else {
                      <span class="state state--wait"><i class="pi pi-hourglass" aria-hidden="true"></i>{{ 'pickups.tab.waiting' | t }}</span>
                    }
                  </div>
                  <p-button
                    [label]="'pickups.signout.confirm' | t"
                    [ariaLabel]="'pickups.mine.signOutLabel' | t: { store: shift.fsp.name, date: (shift.shiftAt | fsDate: 'dateTime') }"
                    icon="pi pi-sign-out"
                    severity="danger"
                    [text]="true"
                    size="small"
                    (onClick)="cleaningSignOut.emit(shift)"
                    data-testid="pickups.mine.cleaning.signOut"
                  />
                </li>
              }
            }
          </ul>
          @if (hidden() > 0) {
            <button pButton type="button" class="more" [text]="true" aria-controls="mine-list" (click)="showMore()" data-testid="pickups.mine.showMore">
              <i pButtonIcon class="pi pi-angle-down" aria-hidden="true"></i>
              <span pButtonLabel>{{ 'pickups.page.showMore' | t: { count: Math.min(hidden(), page) } }}</span>
            </button>
          }
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: './pickups-section.scss',
  styles: `
    .item { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; padding: 0.9rem 1rem; }
    .item__main { display: grid; gap: 0.2rem; min-width: 0; }
    .item__store { font-weight: 700; color: inherit; }
    .item__when { font-variant-numeric: tabular-nums; }
    .item--cleaning { border-left: 4px solid var(--fs-primary); }
    .kind { display: inline-flex; gap: 0.35rem; align-items: center; color: var(--fs-text-muted); font-size: 0.88rem; }
    .state { display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.88rem; font-weight: 600; }
    .state--ok { color: var(--fs-primary-strong); }
    .state--wait { color: var(--fs-warning-text); }
  `,
})
export class MyPickupsListComponent {
  private readonly injector = inject(Injector);

  readonly state = input.required<DataState<MyPickups>>();
  readonly signOut = output<UserPickup>();
  readonly cleaningSignOut = output<UserCleaningShift>();
  /** The empty state's action: jump to "Where can I help?". */
  readonly findOptions = output<void>();

  protected readonly Math = Math;
  protected readonly page = MY_PICKUPS_PAGE;
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  private readonly shown = signal(MY_PICKUPS_PAGE);

  protected readonly entries = computed(() =>
    mergeByTime(this.state().data()?.pickups ?? [], this.state().data()?.cleaning ?? []),
  );
  protected readonly visible = computed(() => this.entries().slice(0, this.shown()));
  protected readonly hidden = computed(() => Math.max(0, this.entries().length - this.shown()));

  /** Reveals the next page and moves the focus to the first revealed store link (keyboard users keep their place). */
  protected showMore(): void {
    const first = this.shown();
    this.shown.update((count) => count + MY_PICKUPS_PAGE);
    afterNextRender(() => this.list()?.nativeElement.children[first]?.querySelector<HTMLElement>('a')?.focus(), {
      injector: this.injector,
    });
  }
}
