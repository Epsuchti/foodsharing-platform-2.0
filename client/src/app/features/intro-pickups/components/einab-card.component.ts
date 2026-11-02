import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { IntroPickupCategory, UserSummary } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { CATEGORY_ICONS } from '../intro-category';

/**
 * What an EinAb card shows. Before booking (public, bookable): date, online or on site, city, free places. The
 * category, business and location text exist only on the person's own booking (decision 153, S17).
 */
export interface EinAbCardData {
  category?: IntroPickupCategory;
  startAt: string;
  online?: boolean;
  city?: string;
  guide?: UserSummary;
  businessName?: string;
  publicGeneralLocation?: string;
  publicInfo?: string;
  visitFairteiler?: boolean;
  minimumPickupCount?: number;
  freeSlots?: number;
}

/** One EinAb as a card; the footer (book button, reason, status) is projected. */
@Component({
  selector: 'fs-einab-card',
  imports: [DateTimePipe, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-card einab' },
  template: `
    <div class="einab__head">
      @if (einab().category; as category) {
        <span class="einab__icon" aria-hidden="true"><i [class]="icons[category]"></i></span>
      } @else {
        <span class="einab__icon" aria-hidden="true"><i [class]="einab().online ? 'pi pi-video' : 'pi pi-map-marker'"></i></span>
      }
      <span class="einab__title">
        @if (einab().category; as category) {
          <strong data-testid="introPickups.card.category">{{ 'common.introCategory.' + category | t }}</strong>
        } @else {
          <strong data-testid="introPickups.card.mode">{{ (einab().online ? 'common.introMode.online' : 'common.introMode.onSite') | t }}</strong>
        }
        <span class="muted" data-testid="introPickups.card.date">{{ einab().startAt | fsDate: 'dateTimeLong' : timeZone() }}</span>
      </span>
    </div>
    <div class="einab__meta">
      @if (einab().businessName) {
        <div><i class="pi pi-shop" aria-hidden="true"></i><span>{{ einab().businessName }}</span></div>
      }
      @if (einab().city) {
        <div data-testid="introPickups.card.city">
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span>{{ einab().city }}</span>
        </div>
      }
      @if (einab().publicGeneralLocation) {
        <div data-testid="introPickups.card.location">
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span>{{ einab().publicGeneralLocation }}</span>
        </div>
      }
      @if (einab().publicInfo) {
        <div><i class="pi pi-info-circle" aria-hidden="true"></i><span>{{ einab().publicInfo }}</span></div>
      }
    </div>
    @if (einab().guide; as guide) {
      <fs-user-chip [name]="guide.displayName" [avatarUrl]="guide.avatarUrl" [subtitle]="'introPickups.card.guide' | t" />
    }
    <div class="tags">
      @if (einab().visitFairteiler) {
        <span class="tag tag--ok" data-testid="introPickups.card.fairteiler"><i class="pi pi-heart" aria-hidden="true"></i>{{ 'introPickups.card.fairteiler' | t }}</span>
      }
      @if (einab().minimumPickupCount) {
        <span class="tag">{{ 'introPickups.card.minimumPickups' | t: { count: einab().minimumPickupCount } }}</span>
      }
      @if (einab().freeSlots !== undefined) {
        <span class="tag" [class.tag--warn]="einab().freeSlots === 1" data-testid="introPickups.card.free">
          {{ 'introPickups.card.freeSlots' | t: { count: einab().freeSlots } }}
        </span>
      }
    </div>
    <div class="einab__foot"><ng-content /></div>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      width: auto;
      margin: 0;
    }
  `,
})
export class EinAbCardComponent {
  readonly einab = input.required<EinAbCardData>();
  readonly timeZone = input<string | undefined>(undefined);
  protected readonly icons = CATEGORY_ICONS;
}
