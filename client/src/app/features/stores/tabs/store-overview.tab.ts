import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StoreDetail, UserTeamStatus } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { StoreMembershipActionsComponent } from '../components/membership-actions.component';
import { StorePageContext } from '../store-page.context';

interface Requirement {
  id: 'verified' | 'hygiene' | 'phone' | 'applicationText';
  active: boolean;
}

/**
 * Overview tab of a store (S1/S2/S15): public information, groceries, pickup time, chain, cooperation dates,
 * contact (only for wall readers, A19), requirements for joining the team and the managers.
 */
@Component({
  selector: 'fs-store-overview-tab',
  imports: [DateTimePipe, MapViewComponent, RouterLink, StoreMembershipActionsComponent, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ctx.data(); as store) {
      <div class="cols" data-testid="stores.overview">
        <div class="stack">
          <section class="fs-card card" data-testid="stores.overview.info">
            <h2 class="card__title"><i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'stores.overview.info' | t }}</h2>
            @if (store.description) {
              <p class="text" data-testid="stores.overview.description">{{ store.description }}</p>
            }
            @if (store.publicInfo) {
              <p class="text" data-testid="stores.overview.publicInfo">{{ store.publicInfo }}</p>
            }
            <dl class="facts">
              @if (store.category) {
                <dt>{{ 'stores.overview.category' | t }}</dt>
                <dd>{{ store.category.name }}</dd>
              }
              @if (store.chain) {
                <dt>{{ 'stores.overview.chain' | t }}</dt>
                <dd><a [routerLink]="['/store-chains', store.chain.id]" data-testid="stores.overview.chain">{{ store.chain.name }}</a></dd>
              }
              @if (store.publicPickupTime && store.publicPickupTime !== 'NOT_SET') {
                <dt>{{ 'stores.overview.pickupTime' | t }}</dt>
                <dd>{{ 'stores.pickupTime.' + store.publicPickupTime | t }}</dd>
              }
              @if (store.cooperationStart) {
                <dt>{{ 'stores.overview.cooperationSince' | t }}</dt>
                <dd data-testid="stores.overview.cooperationSince">{{ store.cooperationStart | fsDate: 'date' }}</dd>
              }
              @if (store.weightCategory) {
                <dt>{{ 'stores.overview.weight' | t }}</dt>
                <dd>{{ 'stores.weight.' + store.weightCategory | t }}</dd>
              }
              <dt>{{ 'stores.overview.team' | t }}</dt>
              <dd data-testid="stores.overview.teamCounts">
                {{ 'stores.overview.teamCounts' | t: { members: store.teamCounts.members, jumpers: store.teamCounts.jumpers } }}
              </dd>
            </dl>
            @if (store.groceries?.length) {
              <h3 class="sub">{{ 'stores.overview.groceries' | t }}</h3>
              <ul class="tags" data-testid="stores.overview.groceries">
                @for (grocery of store.groceries; track grocery.id) {
                  <li class="tag">{{ grocery.name }}</li>
                }
              </ul>
            }
          </section>

          @if (store.permissions.mayReadWall && (store.contact || store.specialNotes)) {
            <section class="fs-card card" data-testid="stores.overview.contact">
              <h2 class="card__title"><i class="pi pi-id-card" aria-hidden="true"></i>{{ 'stores.overview.contact' | t }}</h2>
              @if (store.contact; as contact) {
                <dl class="facts">
                  @if (contact.person) {
                    <dt>{{ 'stores.overview.contactPerson' | t }}</dt>
                    <dd>{{ contact.person }}</dd>
                  }
                  @if (contact.phone) {
                    <dt>{{ 'stores.overview.contactPhone' | t }}</dt>
                    <dd><a [href]="'tel:' + contact.phone">{{ contact.phone }}</a></dd>
                  }
                  @if (contact.email) {
                    <dt>{{ 'stores.overview.contactEmail' | t }}</dt>
                    <dd><a [href]="'mailto:' + contact.email">{{ contact.email }}</a></dd>
                  }
                </dl>
              }
              @if (store.specialNotes) {
                <h3 class="sub">{{ 'stores.overview.specialNotes' | t }}</h3>
                <p class="text" data-testid="stores.overview.specialNotes">{{ store.specialNotes }}</p>
              }
            </section>
          }
        </div>

        <div class="stack">
          @if (markers().length) {
            <section class="fs-card card" data-testid="stores.overview.map">
              <fs-map-view [markers]="markers()" [zoom]="16" height="14rem" [ariaLabel]="'stores.overview.mapLabel' | t" testId="stores.overview.mapView" />
            </section>
          }

          <section class="fs-card card" data-testid="stores.overview.requirements">
            <h2 class="card__title"><i class="pi pi-list-check" aria-hidden="true"></i>{{ 'stores.overview.requirements' | t }}</h2>
            <!-- members and coordinators are not asked to apply (design review M7 R1) -->
            <p class="muted" data-testid="stores.overview.teamExplain">
              {{ (inTeam() ? 'stores.overview.requirementsForNewMembers' : 'stores.teamExplain.' + store.teamStatus) | t }}
            </p>
            @if (requirements().length) {
              <ul class="checklist">
                @for (req of requirements(); track req.id) {
                  <li [attr.data-requirement]="req.id"><i class="pi pi-check-circle" aria-hidden="true"></i>{{ 'stores.requirement.' + req.id | t }}</li>
                }
              </ul>
            } @else {
              <p class="muted">{{ 'stores.overview.noRequirements' | t }}</p>
            }
            <fs-store-membership-actions />
          </section>

          <section class="fs-card card" data-testid="stores.overview.managers">
            <h2 class="card__title"><i class="pi pi-star" aria-hidden="true"></i>{{ 'stores.overview.managers' | t }}</h2>
            <div class="people">
              @for (manager of store.managers; track manager.id) {
                <fs-user-chip [name]="manager.displayName" [userId]="manager.id" [avatarUrl]="manager.avatarUrl" [verified]="manager.verified" />
              } @empty {
                <p class="muted">{{ 'stores.overview.noManagers' | t }}</p>
              }
            </div>
          </section>
        </div>
      </div>
    }
  `,
  styleUrl: '../stores.scss',
  styles: `
    .text { margin: 0; white-space: pre-line; }
    .facts { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.35rem 1rem; margin: 0; }
    .facts dt { color: var(--fs-text-muted); font-size: 0.88rem; }
    .facts dd { margin: 0; }
    .sub { margin: 0.25rem 0 0; font-size: 0.95rem; }
    .tags { display: flex; flex-wrap: wrap; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .tag { padding: 0.15rem 0.6rem; border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.82rem; }
    .checklist { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .checklist li { display: flex; gap: 0.5rem; align-items: center; }
    .checklist .pi { color: var(--fs-primary); }
  `,
})
export class StoreOverviewTab {
  protected readonly ctx = inject(StorePageContext);

  protected readonly markers = computed<MapMarker[]>(() => {
    const store = this.ctx.data();
    if (!store || store.latitude == null || store.longitude == null) return [];
    return [{ id: store.id, kind: 'store', lat: store.latitude, lon: store.longitude, label: store.name }];
  });

  /** Members and coordinators read the requirements as information for new members, not as an invitation to apply. */
  protected readonly inTeam = computed(() => isInTeam(this.ctx.data()?.membership));

  protected readonly requirements = computed<Requirement[]>(() => {
    const store: StoreDetail | undefined = this.ctx.data();
    if (!store) return [];
    const all: Requirement[] = [
      { id: 'verified', active: !!store.requiresVerified },
      { id: 'hygiene', active: !!store.requiresHygieneCertificate },
      { id: 'phone', active: !!store.requiresPhone },
      { id: 'applicationText', active: !!store.requiresApplicationText },
    ];
    return all.filter((req) => req.active);
  });
}

/** True for members and coordinators of the store (they read the requirements as information for new members). */
export function isInTeam(membership: UserTeamStatus | undefined): boolean {
  return membership === UserTeamStatus.Member || membership === UserTeamStatus.Coordinator;
}
