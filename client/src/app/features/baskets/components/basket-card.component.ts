import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BasketStatus, BasketSummary } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { formatDistance, photoVariant } from '../baskets.util';

/**
 * One basket in a list (nearby, mine, requested): photo (sm variant) or a placeholder, description (≤ 200 chars from
 * the server), distance, expiry, owner and — for the owner's list — the request count. The whole card links to the
 * detail page; `highlighted` marks the basket picked on the map.
 */
@Component({
  selector: 'fs-basket-card',
  imports: [DateTimePipe, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-basket-id]': 'basket().id', 'data-testid': 'baskets.card' },
  template: `
    @let b = basket();
    <article class="card fs-card" [class.card--highlighted]="highlighted()">
      <div class="card__photo">
        @if (photo(); as src) {
          <img [src]="src" alt="" width="96" height="96" loading="lazy" />
        } @else {
          <i class="pi pi-shopping-cart" aria-hidden="true"></i>
          <span class="fs-visually-hidden">{{ 'baskets.card.noPhoto' | t }}</span>
        }
      </div>
      <div class="card__body">
        <a class="card__title" [routerLink]="['/baskets', b.id]" data-testid="baskets.card.link">{{ b.description }}</a>
        <p class="card__meta">
          @if (distance(); as km) {
            <span data-testid="baskets.card.distance"><i class="pi pi-map-marker" aria-hidden="true"></i> {{ 'baskets.card.distance' | t: { km } }}</span>
          }
          @if (b.status === active) {
            <span><i class="pi pi-clock" aria-hidden="true"></i> {{ 'baskets.card.expires' | t: { date: (b.expiresAt | fsDate: 'date') } }}</span>
          } @else {
            <span class="tag" data-testid="baskets.card.status">{{ 'baskets.status.' + b.status | t }}</span>
          }
          @if (showRequests()) {
            <span data-testid="baskets.card.requests"><i class="pi pi-inbox" aria-hidden="true"></i> {{ 'baskets.card.requests' | t: { count: b.requestCount ?? 0 } }}</span>
          }
        </p>
        @if (showOwner()) {
          <fs-user-chip
            [name]="b.owner.displayName"
            [userId]="b.owner.id"
            [avatarUrl]="b.owner.avatarUrl"
            [verified]="b.owner.verified"
            [sleepMode]="b.owner.sleepMode"
            [sleepMessage]="b.owner.sleepMessage"
          />
        }
      </div>
    </article>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .card {
      position: relative;
      display: grid;
      grid-template-columns: 4.5rem minmax(0, 1fr);
      gap: 0.85rem;
      align-items: start;
      padding: 0.85rem;
      transition: box-shadow 0.15s ease, border-color 0.15s ease;
    }
    .card:hover, .card:focus-within { border-color: var(--fs-primary); }
    .card--highlighted { border-color: var(--fs-primary); box-shadow: 0 0 0 2px var(--fs-primary-soft); }
    .card__photo {
      display: grid;
      place-items: center;
      width: 4.5rem;
      height: 4.5rem;
      overflow: hidden;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
      color: var(--fs-primary-strong);
      font-size: 1.5rem;
    }
    .card__photo img { width: 100%; height: 100%; object-fit: cover; }
    .card__body { display: grid; gap: 0.4rem; min-width: 0; }
    .card__title {
      color: var(--fs-text);
      font-weight: 650;
      text-decoration: none;
      overflow-wrap: anywhere;
    }
    /* the whole card is the link target */
    .card__title::after { content: ''; position: absolute; inset: 0; }
    .card__title:focus-visible { outline: none; }
    .card:has(.card__title:focus-visible) { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .card__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem 0.9rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .tag {
      padding: 0 0.45rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text);
      font-weight: 600;
    }
    fs-user-chip { position: relative; z-index: 1; }
  `,
})
export class BasketCardComponent {
  private readonly i18n = inject(I18nService);

  readonly basket = input.required<BasketSummary>();
  readonly highlighted = input(false);
  readonly showOwner = input(true);
  readonly showRequests = input(false);

  protected readonly active = BasketStatus.Active;
  protected readonly photo = computed(() => photoVariant(this.basket().photoUrl, 'sm'));
  protected readonly distance = computed(() => formatDistance(this.basket().distanceKm, this.i18n.locale()));
}
