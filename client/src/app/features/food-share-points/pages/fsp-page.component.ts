import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { map } from 'rxjs';

import { FoodSharePointsService, Fsp, FspStatus, UserSummary, WallEntryPage } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { WallComponent, WallPost } from '../../../shared/wall/wall.component';
import { MANAGERS_MAX, addManager, fspMapLinks, photoVariant, removeManager } from '../fsp.util';

/**
 * /food-share-points/:fspId (FS1; the FSP_ACTIVATE and FSP_NEW_POST bells link here): photo, description, address
 * with navigation links and map, managers, follow switches (bell / mail), wall (`fs-wall`). FSP admins activate an
 * INACTIVE FSP and delete; editors (FSP admins and managers) edit and replace the managers. The server decides every
 * right (`Fsp.permissions`); a hidden or unknown FSP renders the not-found state.
 */
@Component({
  selector: 'fs-fsp-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    MapViewComponent,
    PageHeaderComponent,
    RouterLink,
    ToggleSwitch,
    TPipe,
    UserChipComponent,
    UserPickerComponent,
    WallComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="fsp" skeleton="detail" backLink="/food-share-points/mine" data-testid="foodSharePoints.detail.state">
      <ng-template [fsData]="fsp" let-f>
        <fs-page-header
          [title]="f.name"
          [subtitle]="f.region.name"
          icon="pi pi-box"
          [backLink]="['/regions', f.region.id, 'food-share-points']"
          [backLabel]="'foodSharePoints.detail.back' | t: { region: f.region.name }"
        >
          @if (f.permissions.mayEdit) {
            <p-button [label]="'foodSharePoints.detail.edit' | t" icon="pi pi-pencil" severity="secondary" [outlined]="true" [routerLink]="['/food-share-points', f.id, 'edit']" data-testid="foodSharePoints.detail.edit" />
          }
          @if (f.permissions.mayDelete) {
            <p-button [label]="'foodSharePoints.detail.delete' | t" icon="pi pi-trash" severity="danger" [outlined]="true" [loading]="remove.busy()" (onClick)="confirmDelete(f)" data-testid="foodSharePoints.detail.delete" />
          }
        </fs-page-header>

        @if (f.status === inactive) {
          <div class="inactive" role="status" data-testid="foodSharePoints.detail.inactive">
            <p class="inactive__text">
              <i class="pi pi-hourglass" aria-hidden="true"></i>
              <span>
                <strong>{{ 'foodSharePoints.status.INACTIVE' | t }}</strong> ·
                {{ (f.permissions.mayActivate ? 'foodSharePoints.detail.inactiveAdmin' : 'foodSharePoints.detail.inactiveText') | t }}
              </span>
            </p>
            @if (f.permissions.mayActivate) {
              <p-button [label]="'foodSharePoints.detail.activate' | t" icon="pi pi-check-circle" [loading]="activate.busy()" (onClick)="activate.run()" data-testid="foodSharePoints.detail.activate" />
            }
          </div>
        }

        <div class="cols">
          <div class="stack">
            <section class="fs-card card" data-testid="foodSharePoints.detail.content">
              @if (photo(); as src) {
                <img class="photo" [src]="src" [alt]="'foodSharePoints.detail.photo' | t: { name: f.name }" width="640" height="640" data-testid="foodSharePoints.detail.photo" />
              }
              <h2 class="card__title"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'foodSharePoints.detail.about' | t }}</h2>
              @if (f.description) {
                <p class="description" data-testid="foodSharePoints.detail.description">{{ f.description }}</p>
              } @else {
                <p class="muted">{{ 'foodSharePoints.detail.noDescription' | t }}</p>
              }
              <p class="meta">
                <span data-testid="foodSharePoints.detail.followers"><i class="pi pi-users" aria-hidden="true"></i> {{ 'foodSharePoints.card.followers' | t: { count: f.followerCount } }}</span>
                <span><i class="pi pi-calendar-plus" aria-hidden="true"></i> {{ 'foodSharePoints.detail.created' | t: { date: (f.createdAt | fsDate: 'date') } }}</span>
                @if (f.creator; as creator) {
                  <span>{{ 'foodSharePoints.detail.createdBy' | t: { name: creator.displayName } }}</span>
                }
              </p>
            </section>

            <section class="fs-card card" aria-labelledby="fsp-wall-title" data-testid="foodSharePoints.detail.wallSection">
              <h2 class="card__title" id="fsp-wall-title"><i class="pi pi-comments" aria-hidden="true"></i> {{ 'foodSharePoints.detail.wall' | t }}</h2>
              <fs-wall
                [posts]="wallPosts()"
                [state]="wall"
                [mayPost]="f.permissions.mayPost"
                [posting]="post.busy()"
                (post)="post.run($event)"
                (delete)="deletePost.run($event)"
                testId="foodSharePoints.detail.wall"
              />
            </section>
          </div>

          <aside class="stack">
            <section class="fs-card card" data-testid="foodSharePoints.detail.address">
              <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i> {{ 'foodSharePoints.detail.address' | t }}</h2>
              <address class="address" data-testid="foodSharePoints.detail.addressText">
                {{ f.address.street }}<br />{{ f.address.postcode }} {{ f.address.city }}
              </address>
              @if (links(); as l) {
                <div class="links">
                  <a class="link" [href]="l.google" target="_blank" rel="noopener noreferrer" data-testid="foodSharePoints.detail.googleMaps">
                    <i class="pi pi-directions" aria-hidden="true"></i> {{ 'foodSharePoints.detail.googleMaps' | t }}
                    <span class="fs-visually-hidden">{{ 'foodSharePoints.detail.newTab' | t }}</span>
                  </a>
                  <a class="link" [href]="l.apple" target="_blank" rel="noopener noreferrer" data-testid="foodSharePoints.detail.appleMaps">
                    <i class="pi pi-directions" aria-hidden="true"></i> {{ 'foodSharePoints.detail.appleMaps' | t }}
                    <span class="fs-visually-hidden">{{ 'foodSharePoints.detail.newTab' | t }}</span>
                  </a>
                </div>
              }
              @if (center(); as c) {
                <fs-map-view
                  [markers]="markers()"
                  [center]="c"
                  [zoom]="15"
                  [fitMarkers]="false"
                  height="16rem"
                  [ariaLabel]="'foodSharePoints.detail.mapLabel' | t"
                  testId="foodSharePoints.detail.map"
                />
              }
            </section>

            @if (f.following.following || f.permissions.mayFollow) {
              <section class="fs-card card" data-testid="foodSharePoints.detail.follow" [attr.data-following]="f.following.following">
                <h2 class="card__title"><i class="pi pi-bell" aria-hidden="true"></i> {{ 'foodSharePoints.follow.title' | t }}</h2>
                <p class="muted">
                  {{ (isManager() ? 'foodSharePoints.follow.manager' : f.following.following ? 'foodSharePoints.follow.following' : 'foodSharePoints.follow.text') | t }}
                </p>
                <div class="switch">
                  <p-toggleswitch
                    inputId="fsp-follow-bell"
                    [ngModel]="f.following.following ? f.following.bell : bellDraft()"
                    (ngModelChange)="setBell(f, $event)"
                    [disabled]="follow.busy()"
                    data-testid="foodSharePoints.follow.bell"
                  />
                  <label for="fsp-follow-bell">{{ 'foodSharePoints.follow.bell' | t }}</label>
                </div>
                <div class="switch">
                  <p-toggleswitch
                    inputId="fsp-follow-mail"
                    [ngModel]="f.following.following ? f.following.mail : mailDraft()"
                    (ngModelChange)="setMail(f, $event)"
                    [disabled]="follow.busy()"
                    data-testid="foodSharePoints.follow.mail"
                  />
                  <label for="fsp-follow-mail">{{ 'foodSharePoints.follow.mail' | t }}</label>
                </div>
                @if (!f.following.following) {
                  <p-button [label]="'foodSharePoints.follow.follow' | t" icon="pi pi-bell" [loading]="follow.busy()" (onClick)="follow.run(bellDraft(), mailDraft())" data-testid="foodSharePoints.follow.submit" />
                } @else if (!isManager()) {
                  <p-button [label]="'foodSharePoints.follow.unfollow' | t" icon="pi pi-bell-slash" severity="secondary" [outlined]="true" [loading]="unfollow.busy()" (onClick)="unfollow.run()" data-testid="foodSharePoints.follow.unfollow" />
                }
              </section>
            }

            <section class="fs-card card" data-testid="foodSharePoints.detail.managers">
              <div class="card__head">
                <h2 class="card__title"><i class="pi pi-user-edit" aria-hidden="true"></i> {{ 'foodSharePoints.managers.title' | t }}</h2>
                @if (f.permissions.mayEdit && !editingManagers()) {
                  <p-button [label]="'foodSharePoints.managers.edit' | t" icon="pi pi-pencil" size="small" severity="secondary" [text]="true" (onClick)="startManagers(f)" data-testid="foodSharePoints.managers.edit" />
                }
              </div>
              @if (editingManagers()) {
                <ul class="managers">
                  @for (manager of managerDraft(); track manager.id) {
                    <li class="manager" data-testid="foodSharePoints.managers.draftItem" [attr.data-user-id]="manager.id">
                      <fs-user-chip [name]="manager.displayName" [userId]="manager.id" [avatarUrl]="manager.avatarUrl" [verified]="manager.verified" />
                      <p-button
                        icon="pi pi-times"
                        size="small"
                        severity="secondary"
                        [text]="true"
                        [rounded]="true"
                        [disabled]="managerDraft().length <= 1"
                        [ariaLabel]="'foodSharePoints.managers.remove' | t: { name: manager.displayName }"
                        (onClick)="dropManager(manager)"
                        data-testid="foodSharePoints.managers.remove"
                      />
                    </li>
                  }
                </ul>
                @if (managerDraft().length < managersMax) {
                  <fs-user-picker
                    [regionId]="f.region.id"
                    [excludeIds]="managerIds()"
                    [label]="'foodSharePoints.managers.add' | t"
                    testId="foodSharePoints.managers.picker"
                    (picked)="pickManager($event)"
                  />
                }
                <p class="muted">{{ 'foodSharePoints.managers.hint' | t: { max: managersMax } }}</p>
                @if (saveManagers.error(); as error) {
                  <p class="error" role="alert" [attr.data-code]="error.code" data-testid="foodSharePoints.managers.error">{{ i18n.error(error.code, error.args ?? {}) }}</p>
                }
                <div class="actions">
                  <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="editingManagers.set(false)" data-testid="foodSharePoints.managers.cancel" />
                  <p-button [label]="'foodSharePoints.managers.save' | t" icon="pi pi-check" [loading]="saveManagers.busy()" (onClick)="saveManagers.run()" data-testid="foodSharePoints.managers.save" />
                </div>
              } @else {
                <ul class="managers" data-testid="foodSharePoints.managers.list">
                  @for (manager of f.managers; track manager.id) {
                    <li class="manager" data-testid="foodSharePoints.managers.item" [attr.data-user-id]="manager.id">
                      <fs-user-chip
                        [name]="manager.displayName"
                        [userId]="manager.id"
                        [avatarUrl]="manager.avatarUrl"
                        [verified]="manager.verified"
                        [sleepMode]="manager.sleepMode"
                        [sleepMessage]="manager.sleepMessage"
                        [link]="['/profile', manager.id]"
                      />
                    </li>
                  } @empty {
                    <li class="muted">{{ 'foodSharePoints.managers.none' | t }}</li>
                  }
                </ul>
              }
            </section>
          </aside>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .cols { display: grid; gap: 1.25rem; }
    @media (min-width: 60rem) { .cols { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); align-items: start; } }
    .stack { display: grid; gap: 1.25rem; min-width: 0; }
    .card { display: grid; gap: 0.75rem; min-width: 0; }
    .card__head { display: flex; gap: 0.5rem; align-items: center; justify-content: space-between; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .error { margin: 0; color: var(--fs-danger); }
    .photo { width: 100%; max-width: 28rem; height: auto; aspect-ratio: 1; object-fit: cover; border-radius: var(--fs-radius-md); }
    .description { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.3rem 1rem; margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; }
    .inactive {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.25rem;
      padding: 0.75rem 1rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .inactive__text { display: flex; gap: 0.5rem; align-items: center; margin: 0; }
    .address { font-style: normal; line-height: 1.5; }
    .links { display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; }
    .link { display: inline-flex; gap: 0.35rem; align-items: center; font-weight: 600; }
    .switch { display: flex; gap: 0.75rem; align-items: center; }
    .managers { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .manager { display: flex; gap: 0.5rem; align-items: center; justify-content: space-between; min-width: 0; }
    .actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
  `,
})
export class FspPageComponent {
  private readonly api = inject(FoodSharePointsService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly inactive = FspStatus.Inactive;
  protected readonly managersMax = MANAGERS_MAX;

  protected readonly fspId = toSignal(this.route.paramMap.pipe(map((params) => params.get('fspId') ?? undefined)));

  protected readonly fsp = loadResource<Fsp, string>({
    params: () => this.fspId(),
    loader: (fspId) => this.api.getFoodSharePoint({ fspId }),
    isEmpty: () => false,
  });

  protected readonly photo = computed(() => photoVariant(this.fsp.data()?.photoUrl, 'lg'));
  protected readonly links = computed(() => {
    const f = this.fsp.data();
    return f ? fspMapLinks(f) : null;
  });
  protected readonly center = computed<[number, number] | undefined>(() => {
    const f = this.fsp.data();
    return f?.lat != null && f.lon != null ? [f.lat, f.lon] : undefined;
  });
  protected readonly markers = computed<MapMarker[]>(() => {
    const f = this.fsp.data();
    return f?.lat != null && f.lon != null ? [{ id: f.id, kind: 'fsp', lat: f.lat, lon: f.lon, label: f.name }] : [];
  });
  protected readonly isManager = computed(() => {
    const me = this.session.user()?.id;
    return !!me && !!this.fsp.data()?.managers.some((manager) => manager.id === me);
  });

  // --- wall (everyone who sees the FSP reads; members, followers and editors write) ---
  /** Id of the shown FSP: stays equal while the FSP reloads, so the wall loads once per FSP. */
  private readonly shownFspId = computed(() => this.fsp.data()?.id);
  protected readonly wall = loadResource<WallEntryPage, string>({
    params: () => this.shownFspId(),
    loader: (fspId) => this.api.listFoodSharePointWall({ fspId, size: 50 }),
    isEmpty: (page) => page.items.length === 0,
  });
  protected readonly wallPosts = computed<WallPost[]>(() => this.wall.data()?.items ?? []);
  protected readonly post = runMutation(
    (body: string) => this.api.createFoodSharePointWallPost({ fspId: this.fspId()!, wallEntryCreate: { body } }),
    { onSuccess: () => this.wall.reload() },
  );
  protected readonly deletePost = runMutation(
    (post: WallPost) => this.api.deleteFoodSharePointWallPost({ fspId: this.fspId()!, postId: post.id }),
    { successToastKey: 'foodSharePoints.toast.postDeleted', onSuccess: () => this.wall.reload() },
  );

  // --- follow: before following the switches are a draft; afterwards every switch change is saved ---
  protected readonly bellDraft = signal(true);
  protected readonly mailDraft = signal(false);
  protected readonly follow = runMutation(
    (bell: boolean, mail: boolean) => this.api.followFoodSharePoint({ fspId: this.fspId()!, fspFollowUpdate: { bell, mail } }),
    { successToastKey: 'foodSharePoints.toast.followSaved', onSuccess: () => this.fsp.reload() },
  );
  protected readonly unfollow = runMutation(() => this.api.unfollowFoodSharePoint({ fspId: this.fspId()! }), {
    successToastKey: 'foodSharePoints.toast.unfollowed',
    onSuccess: () => this.fsp.reload(),
  });

  protected setBell(f: Fsp, bell: boolean): void {
    if (f.following.following) void this.follow.run(bell, f.following.mail);
    else this.bellDraft.set(bell);
  }

  protected setMail(f: Fsp, mail: boolean): void {
    if (f.following.following) void this.follow.run(f.following.bell, mail);
    else this.mailDraft.set(mail);
  }

  // --- status / delete (FSP admins) ---
  protected readonly activate = runMutation(
    () => this.api.setFoodSharePointStatus({ fspId: this.fspId()!, fspStatusUpdate: { status: FspStatus.Active } }),
    { successToastKey: 'foodSharePoints.toast.activated', onSuccess: () => this.fsp.reload() },
  );
  private readonly regionAfterDelete = signal<string | null>(null);
  protected readonly remove = runMutation(() => this.api.deleteFoodSharePoint({ fspId: this.fspId()! }), {
    successToastKey: 'foodSharePoints.toast.deleted',
    onSuccess: () => void this.router.navigate(['/regions', this.regionAfterDelete(), 'food-share-points']),
  });

  protected confirmDelete(f: Fsp): void {
    this.regionAfterDelete.set(f.region.id);
    this.confirm.confirm({
      header: this.i18n.t('foodSharePoints.detail.deleteTitle', { name: f.name }),
      message: this.i18n.t('foodSharePoints.detail.deleteText'),
      acceptLabel: this.i18n.t('foodSharePoints.detail.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }

  // --- managers (editors replace the whole list) ---
  protected readonly editingManagers = signal(false);
  protected readonly managerDraft = signal<UserSummary[]>([]);
  protected readonly managerIds = computed(() => this.managerDraft().map((manager) => manager.id));
  protected readonly saveManagers = runMutation(
    () => this.api.setFoodSharePointManagers({ fspId: this.fspId()!, fspManagersUpdate: { userIds: this.managerIds() } }),
    {
      errorToast: false,
      successToastKey: 'foodSharePoints.toast.managersSaved',
      onSuccess: () => {
        this.editingManagers.set(false);
        this.fsp.reload();
      },
    },
  );

  protected startManagers(f: Fsp): void {
    this.managerDraft.set([...f.managers]);
    this.editingManagers.set(true);
  }

  protected pickManager(user: UserSummary): void {
    const ids = addManager(this.managerIds(), user.id);
    if (ids.length > this.managerDraft().length) this.managerDraft.update((list) => [...list, user]);
  }

  protected dropManager(user: UserSummary): void {
    const ids = removeManager(this.managerIds(), user.id);
    this.managerDraft.update((list) => list.filter((manager) => ids.includes(manager.id)));
  }
}
