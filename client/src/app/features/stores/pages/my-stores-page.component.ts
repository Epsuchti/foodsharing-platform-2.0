import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { StoreListItem, StoreTeamService, StoresService, UserTeamStatus } from '../../../api';
import { homeRegion } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { STORE_BADGES } from '../../../shared/store-badges/store-badges.component';
import { INACTIVE_COOPERATION } from '../stores.util';

type GroupId = 'manager' | 'member' | 'jumper' | 'applied' | 'invited';

interface StoreGroup {
  id: GroupId;
  icon: string;
  stores: StoreListItem[];
}

function groupOf(store: StoreListItem): GroupId {
  if (store.isManager) return 'manager';
  switch (store.membership) {
    case UserTeamStatus.WaitingList:
      return 'jumper';
    case UserTeamStatus.Applied:
      return 'applied';
    case UserTeamStatus.Invited:
      return 'invited';
    default:
      return 'member';
  }
}

const GROUPS: { id: GroupId; icon: string }[] = [
  { id: 'manager', icon: 'pi pi-star' },
  { id: 'member', icon: 'pi pi-users' },
  { id: 'jumper', icon: 'pi pi-clock' },
  { id: 'invited', icon: 'pi pi-envelope' },
  { id: 'applied', icon: 'pi pi-send' },
];

/**
 * /stores/mine (S1): the user's stores grouped by membership (manager, member, jumper, invited, applied) with
 * cooperation/team badges, the next pickup and the free-slot light; inactive stores behind a toggle.
 */
@Component({
  selector: 'fs-my-stores-page',
  imports: [...DATA_STATE_IMPORTS, ...STORE_BADGES, Button, DateTimePipe, FormsModule, PageHeaderComponent, RouterLink, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'stores.mine.title' | t" [subtitle]="'stores.mine.subtitle' | t" icon="pi pi-shopping-bag" />

    <div class="toolbar">
      <label class="switch-row__label" for="stores-inactive">
        <p-toggleswitch inputId="stores-inactive" [ngModel]="showInactive()" (ngModelChange)="showInactive.set($event)" data-testid="stores.mine.inactive" />
        {{ 'stores.mine.showInactive' | t }}
      </label>
      @if (homeRegionId(); as regionId) {
        <p-button
          [label]="'stores.mine.regionStores' | t"
          icon="pi pi-list"
          severity="secondary"
          [outlined]="true"
          [routerLink]="['/regions', regionId, 'stores']"
          data-testid="stores.mine.regionStores"
        />
      }
    </div>

    <fs-data-state
      [state]="stores"
      skeleton="list"
      [emptyTitle]="'stores.mine.empty' | t"
      [emptyText]="'stores.mine.emptyText' | t"
      testId="stores.mine.state"
      data-testid="stores.mine.state"
    >
      <ng-template [fsData]="stores">
        <div class="stack">
          @for (group of groups(); track group.id) {
            <section class="fs-card card" [attr.data-testid]="'stores.mine.group.' + group.id">
              <h2 class="card__title">
                <i [class]="group.icon" aria-hidden="true"></i>{{ 'stores.mine.group.' + group.id | t }}
                <span class="count">{{ group.stores.length }}</span>
              </h2>
              <ul class="stores">
                @for (store of group.stores; track store.id) {
                  <li>
                    <a class="store" [routerLink]="['/stores', store.id]" data-testid="stores.mine.store" [attr.data-store]="store.name">
                      <span class="store__name">{{ store.name }}</span>
                      <span class="store__meta">
                        {{ store.address.street }}, {{ store.address.city }}
                        @if (store.region) {
                          · {{ store.region.name }}
                        }
                      </span>
                      <span class="store__badges">
                        <fs-cooperation-status-badge [status]="store.cooperationStatus" />
                        <fs-team-status-badge [status]="store.teamStatus" />
                      </span>
                      <span class="store__pickup" data-testid="stores.mine.nextPickup">
                        @if (store.nextPickupAt) {
                          <i class="pi pi-calendar" aria-hidden="true"></i>
                          {{ 'stores.mine.nextPickup' | t: { date: (store.nextPickupAt | fsDate: 'dateTime') } }}
                          <fs-free-slot-dot [status]="store.freeSlotStatus" [count]="store.freeSlotCount" />
                        } @else {
                          <span class="muted">{{ 'stores.mine.noPickup' | t }}</span>
                        }
                      </span>
                    </a>
                    @if (group.id === 'invited') {
                      <div class="invite-actions" data-testid="stores.mine.invitation" [attr.data-store]="store.name">
                        <p-button
                          [label]="'stores.membership.acceptInvitation' | t"
                          icon="pi pi-check"
                          size="small"
                          [loading]="answer.busy()"
                          (onClick)="answer.run(store.id, true)"
                          data-testid="stores.mine.acceptInvitation"
                        />
                        <p-button
                          [label]="'stores.membership.declineInvitation' | t"
                          size="small"
                          severity="secondary"
                          [outlined]="true"
                          [disabled]="answer.busy()"
                          (onClick)="answer.run(store.id, false)"
                          data-testid="stores.mine.declineInvitation"
                        />
                      </div>
                    } @else if (group.id === 'applied') {
                      <div class="invite-actions" data-testid="stores.mine.application" [attr.data-store]="store.name">
                        <p-button
                          [label]="'stores.membership.cancelApplication' | t"
                          icon="pi pi-undo"
                          size="small"
                          severity="secondary"
                          [outlined]="true"
                          [loading]="cancelApplication.busy()"
                          (onClick)="cancelApplication.run(store.id)"
                          data-testid="stores.mine.cancelApplication"
                        />
                      </div>
                    }
                  </li>
                }
              </ul>
            </section>
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../stores.scss',
  styles: `
    :host { display: block; width: min(100%, 72rem); margin: 0 auto; }
    .toolbar { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 0.75rem; margin-bottom: 1rem; }
    .switch-row__label { display: inline-flex; align-items: center; gap: 0.6rem; }
    .count { margin-left: auto; color: var(--fs-text-muted); font-size: 0.85rem; font-weight: 600; }
    .stores { display: grid; gap: 0.35rem; margin: 0; padding: 0; list-style: none; }
    .store {
      display: grid; gap: 0.3rem; padding: 0.7rem 0.8rem; border-radius: var(--fs-radius-md, 0.6rem);
      color: inherit; text-decoration: none; border: 1px solid var(--fs-border);
    }
    .invite-actions { display: flex; flex-wrap: wrap; gap: 0.4rem; padding: 0.4rem 0.8rem 0; }
    .store:hover { background: var(--fs-surface-muted); }
    .store__name { font-weight: 650; }
    .store__meta { color: var(--fs-text-muted); font-size: 0.85rem; }
    .store__badges { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .store__pickup { display: inline-flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; font-size: 0.88rem; }
    @media (min-width: 48rem) {
      .store { grid-template-columns: minmax(0, 1fr) auto; }
      .store__badges, .store__pickup { justify-self: end; }
    }
  `,
})
export class MyStoresPageComponent {
  private readonly api = inject(StoresService);
  private readonly session = inject(SessionService);

  protected readonly showInactive = signal(false);
  protected readonly homeRegionId = computed(() => homeRegion(this.session.session())?.id ?? null);

  /** All stores incl. applications and invitations; closed stores are filtered here (toggle). */
  protected readonly stores = loadResource<StoreListItem[]>({
    loader: () => this.api.listMyStores({ excludeInactive: false }),
  });

  private readonly teamApi = inject(StoreTeamService);

  /** Accept or decline an invitation right from the list (S7). */
  protected readonly answer = runMutation(
    (storeId: string, accept: boolean) =>
      accept ? this.teamApi.acceptInvitation({ storeId }) : this.teamApi.declineInvitation({ storeId }),
    { successToastKey: 'stores.membership.invitationAnswered', onSuccess: () => this.stores.reload() },
  );

  /** Withdraw an own application (S6). */
  protected readonly cancelApplication = runMutation(
    (storeId: string) => this.teamApi.cancelMyStoreApplication({ storeId }),
    { successToastKey: 'stores.membership.applicationCancelled', onSuccess: () => this.stores.reload() },
  );

  protected readonly groups = computed<StoreGroup[]>(() => {
    const showInactive = this.showInactive();
    const stores = (this.stores.data() ?? []).filter(
      (store) => showInactive || !INACTIVE_COOPERATION.includes(store.cooperationStatus),
    );
    return GROUPS.map((group) => ({ ...group, stores: stores.filter((store) => groupOf(store) === group.id) })).filter(
      (group) => group.stores.length > 0,
    );
  });
}
