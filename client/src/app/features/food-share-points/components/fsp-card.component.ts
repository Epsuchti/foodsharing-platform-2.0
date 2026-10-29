import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { FspStatus, FspSummary } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { photoVariant } from '../fsp.util';

/**
 * One food share point in a list (region tab, mine): photo (sm variant) or a placeholder, name, city, follower count,
 * "following" and — for INACTIVE ones — the "awaiting activation" tag. The whole card links to the detail page;
 * `highlighted` marks the FSP picked on the map.
 */
@Component({
  selector: 'fs-fsp-card',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.data-fsp-id]': 'fsp().id', '[attr.data-status]': 'fsp().status', 'data-testid': 'foodSharePoints.card' },
  template: `
    @let f = fsp();
    <article class="card fs-card" [class.card--highlighted]="highlighted()">
      <div class="card__photo">
        @if (photo(); as src) {
          <img [src]="src" alt="" width="96" height="96" loading="lazy" />
        } @else {
          <i class="pi pi-box" aria-hidden="true"></i>
        }
      </div>
      <div class="card__body">
        <a class="card__title" [routerLink]="['/food-share-points', f.id]" data-testid="foodSharePoints.card.link">{{ f.name }}</a>
        <p class="card__meta">
          <span><i class="pi pi-map-marker" aria-hidden="true"></i> {{ f.city }}</span>
          <span data-testid="foodSharePoints.card.followers"><i class="pi pi-users" aria-hidden="true"></i> {{ 'foodSharePoints.card.followers' | t: { count: f.followerCount } }}</span>
          @if (f.following) {
            <span class="tag tag--following" data-testid="foodSharePoints.card.following"><i class="pi pi-bell" aria-hidden="true"></i> {{ 'foodSharePoints.card.following' | t }}</span>
          }
          @if (f.status === inactive) {
            <span class="tag tag--inactive" data-testid="foodSharePoints.card.inactive">{{ 'foodSharePoints.status.INACTIVE' | t }}</span>
          }
        </p>
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
      align-items: center;
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
    .card__title { color: var(--fs-text); font-weight: 650; text-decoration: none; overflow-wrap: anywhere; }
    /* the whole card is the link target */
    .card__title::after { content: ''; position: absolute; inset: 0; }
    .card__title:focus-visible { outline: none; }
    .card:has(.card__title:focus-visible) { outline: 2px solid var(--fs-primary); outline-offset: 2px; }
    .card__meta { display: flex; flex-wrap: wrap; gap: 0.25rem 0.9rem; margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; }
    .tag { padding: 0 0.45rem; border-radius: 999px; color: var(--fs-text); font-weight: 600; }
    .tag--following { background: var(--fs-primary-soft); }
    .tag--inactive { border: 1px solid var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
  `,
})
export class FspCardComponent {
  readonly fsp = input.required<FspSummary>();
  readonly highlighted = input(false);

  protected readonly inactive = FspStatus.Inactive;
  protected readonly photo = computed(() => photoVariant(this.fsp().photoUrl, 'sm'));
}
