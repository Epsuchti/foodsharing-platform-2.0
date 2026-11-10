import { ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button, ButtonDirective, ButtonIcon, ButtonLabel } from '@openng/optimus-ui/button';

import { PickupOption } from '../../../api';
import { DataState } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { SlotSummary, countSummary, isSignedUp, openOptions } from '../pickups.util';
import { PickupSummaryComponent } from './pickup-summary.component';

/** Cards shown at first and per "Show more" (docs/design/ui-polish-batch-proposal.md §3). */
export const OPTIONS_PAGE = 10;

/**
 * "Where can I help?" section of /pickups (PU7): pickups with free slots in my stores that I may take, sorted by time,
 * first 10 and "Show N more"; Sign up opens the page's sign-up dialog, my own sign-ups show a "Signed up" chip instead
 * (review fix A2). `id="options"` for the `#options` fragment.
 */
@Component({
  selector: 'fs-pickup-options-list',
  imports: [...DATA_STATE_IMPORTS, Button, ButtonDirective, ButtonIcon, ButtonLabel, DateTimePipe, PickupSummaryComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="section" id="options" aria-labelledby="options-title" tabindex="-1" data-testid="pickups.options">
      <div class="section__head">
        <h2 class="section__title" id="options-title"><i class="pi pi-plus" aria-hidden="true"></i>{{ 'pickups.options.title' | t }}</h2>
        @if (sorted().length > 0) {
          <span class="section__count" data-testid="pickups.options.count">{{ sorted().length }}</span>
        }
        <p class="section__sub">{{ 'pickups.options.subtitle' | t }}</p>
      </div>
      <fs-data-state
        [state]="state()"
        skeleton="list"
        [skeletonRows]="4"
        [emptyTitle]="'pickups.options.empty' | t"
        [emptyText]="'pickups.options.emptyText' | t"
        emptyIcon="pi pi-check-circle"
        testId="pickups.options.state"
        data-testid="pickups.options.state"
      >
        <ng-template [fsData]="state()">
          <ul class="cards" id="options-list" #list>
            @for (option of visible(); track option.store.id + option.pickupAt) {
              <li class="fs-card option" data-testid="pickups.options.option" [attr.data-store]="option.store.name" [attr.data-pickup-at]="option.pickupAt">
                <div class="option__main">
                  <a class="option__store" [routerLink]="['/stores', option.store.id, 'pickups']">{{ option.store.name }}</a>
                  <span class="option__when">{{ option.pickupAt | fsDate: 'dateTime' }}</span>
                  @if (option.description) {
                    <span class="muted">{{ option.description }}</span>
                  }
                  <span class="option__slots">
                    <span class="muted" data-testid="pickups.options.free">
                      {{ 'pickups.options.free' | t: { free: option.slotCount - option.occupiedSlots.length, total: option.slotCount } }}
                    </span>
                    @if (option.occupiedSlots.length > 0) {
                      @let summary = summaryOf(option);
                      <fs-pickup-summary
                        [confirmed]="summary.confirmed"
                        [unconfirmed]="summary.unconfirmed"
                        [total]="summary.total"
                        testId="pickups.options.summary"
                      />
                    }
                  </span>
                </div>
                @if (isSignedUp(option)) {
                  <span
                    class="signed-up"
                    [class.signed-up--wait]="!option.isConfirmed"
                    [attr.data-confirmed]="option.isConfirmed"
                    data-testid="pickups.options.signedUp"
                  >
                    <i class="pi" [class.pi-check-circle]="option.isConfirmed" [class.pi-hourglass]="!option.isConfirmed" aria-hidden="true"></i>
                    {{ (option.isConfirmed ? 'pickups.options.signedUpConfirmed' : 'pickups.options.signedUpUnconfirmed') | t }}
                  </span>
                } @else {
                  <p-button
                    [label]="'pickups.options.signUp' | t"
                    [ariaLabel]="'pickups.options.signUpLabel' | t: { store: option.store.name, date: (option.pickupAt | fsDate: 'dateTime') }"
                    icon="pi pi-plus"
                    size="small"
                    (onClick)="signUp.emit(option)"
                    data-testid="pickups.options.signUp"
                  />
                }
              </li>
            }
          </ul>
          @if (hidden() > 0) {
            <button pButton type="button" class="more" [text]="true" aria-controls="options-list" (click)="showMore()" data-testid="pickups.options.showMore">
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
    .section:focus { outline: none; }
    /* the action stays beside the text on phones too (one row per pickup instead of three, M7 R2 list density) */
    .option {
      display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: 0.75rem; padding: 0.85rem 1rem;
    }
    .option__main { display: grid; gap: 0.15rem; min-width: 0; }
    .option__store { font-weight: 700; color: inherit; }
    .option__when { font-variant-numeric: tabular-nums; }
    .muted { color: var(--fs-text-muted); font-size: 0.88rem; }
    .option__slots { display: flex; flex-wrap: wrap; align-items: center; column-gap: 0.75rem; row-gap: 0.15rem; }
    /* own sign-up (review fix A2): a state, not an action; same colours as "My pickups" */
    .signed-up { display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.88rem; font-weight: 600; color: var(--fs-primary-strong); text-align: end; max-width: 9.5rem; }
    .signed-up--wait { color: var(--fs-warning-text); }
  `,
})
export class PickupOptionsListComponent {
  private readonly injector = inject(Injector);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly state = input.required<DataState<PickupOption[]>>();
  readonly signUp = output<PickupOption>();

  protected readonly Math = Math;
  protected readonly isSignedUp = isSignedUp;
  protected readonly page = OPTIONS_PAGE;
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  private readonly shown = signal(OPTIONS_PAGE);

  /** Full pickups are never offered (PU7); own sign-ups stay (A2). */
  protected readonly sorted = computed(() => openOptions(this.state().data() ?? []));
  protected readonly visible = computed(() => this.sorted().slice(0, this.shown()));
  protected readonly hidden = computed(() => Math.max(0, this.sorted().length - this.shown()));

  /** The options list only has the counts: unconfirmed = taken − confirmed, never below 0. */
  protected summaryOf(option: PickupOption): SlotSummary {
    return countSummary(option.occupiedSlots.length, option.confirmedCount, option.slotCount);
  }

  /** Reveals the next page and moves the focus to the first revealed store link. */
  protected showMore(): void {
    const first = this.shown();
    this.shown.update((count) => count + OPTIONS_PAGE);
    afterNextRender(() => this.list()?.nativeElement.children[first]?.querySelector<HTMLElement>('a')?.focus(), {
      injector: this.injector,
    });
  }

  /** Scrolls to the section and focuses it (the empty "My pickups" action). */
  focusSection(): void {
    const section = this.host.nativeElement.querySelector<HTMLElement>('#options');
    section?.scrollIntoView({ block: 'start' });
    section?.focus({ preventScroll: true });
  }
}
