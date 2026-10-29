import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Textarea } from '@openng/optimus-ui/textarea';
import { map } from 'rxjs';

import {
  Basket,
  BasketRequest,
  BasketRequestAnswerStatusEnum,
  BasketStatus,
  BasketsService,
  Conversation,
  MessagingService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MapMarker, MapViewComponent } from '../../../shared/map-view/map-view.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { PhoneButtonComponent } from '../../../shared/phone/phone-button.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { REQUEST_MESSAGE_MAX, answerActions, isOpenRequest, photoVariant } from '../baskets.util';

/**
 * /baskets/:basketId (B1, B2): photo, description, kinds, groceries, pick-up map, owner and contact options. Others
 * request the basket (optional message → direct chat message, A38) or withdraw their open request; the owner sees
 * the requests and answers them (PICKED_UP marks the basket taken), edits and deletes; ORGA may delete. The server
 * decides every right (`Basket.permissions`); a 404 renders the not-found state.
 */
@Component({
  selector: 'fs-basket-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Dialog,
    FormsModule,
    MapViewComponent,
    PageHeaderComponent,
    PhoneButtonComponent,
    RouterLink,
    Textarea,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="basket" skeleton="detail" backLink="/baskets" data-testid="baskets.detail.state">
      <ng-template [fsData]="basket" let-b>
        <fs-page-header
          [title]="'baskets.detail.title' | t"
          [subtitle]="'baskets.detail.by' | t: { name: b.owner.displayName }"
          icon="pi pi-shopping-cart"
          backLink="/baskets"
          [backLabel]="'baskets.nearby.title' | t"
        >
          @if (b.permissions.mayEdit) {
            <p-button [label]="'baskets.detail.edit' | t" icon="pi pi-pencil" severity="secondary" [outlined]="true" [routerLink]="['/baskets', b.id, 'edit']" data-testid="baskets.detail.edit" />
          }
          @if (b.permissions.mayDelete) {
            <p-button [label]="'baskets.detail.delete' | t" icon="pi pi-trash" severity="danger" [outlined]="true" [loading]="remove.busy()" (onClick)="confirmDelete()" data-testid="baskets.detail.delete" />
          }
        </fs-page-header>

        <div class="cols">
          <div class="stack">
            <section class="fs-card card" data-testid="baskets.detail.content">
              @if (b.status !== active) {
                <p class="status" role="status" data-testid="baskets.detail.status">
                  <i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'baskets.status.' + b.status | t }} · {{ 'baskets.detail.notActive' | t }}
                </p>
              }
              @if (photo(); as src) {
                <img class="photo" [src]="src" [alt]="'baskets.detail.photo' | t" width="640" height="640" data-testid="baskets.detail.photo" />
              }
              <h2 class="card__title">{{ 'baskets.detail.description' | t }}</h2>
              <p class="description" data-testid="baskets.detail.description">{{ b.description }}</p>
              @if (b.kinds.length) {
                <h3 class="sub">{{ 'baskets.detail.kinds' | t }}</h3>
                <ul class="chips">
                  @for (kind of b.kinds; track kind) {
                    <li class="chip" data-testid="baskets.detail.kind" [attr.data-kind]="kind">{{ 'baskets.kind.' + kind | t }}</li>
                  }
                </ul>
              }
              @if (b.groceries.length) {
                <h3 class="sub">{{ 'baskets.detail.groceries' | t }}</h3>
                <ul class="chips">
                  @for (grocery of b.groceries; track grocery.id) {
                    <li class="chip" data-testid="baskets.detail.grocery">{{ grocery.name }}</li>
                  }
                </ul>
              }
              <p class="meta">
                @if (b.weightGrams) {
                  <span data-testid="baskets.detail.weight"><i class="pi pi-box" aria-hidden="true"></i> {{ 'baskets.detail.weight' | t: { kg: b.weightGrams / 1000 } }}</span>
                }
                <span><i class="pi pi-calendar-plus" aria-hidden="true"></i> {{ 'baskets.detail.created' | t: { date: (b.createdAt | fsDate: 'dateTime') } }}</span>
                @if (b.status === active) {
                  <span data-testid="baskets.detail.expires"><i class="pi pi-clock" aria-hidden="true"></i> {{ 'baskets.detail.expires' | t: { date: (b.expiresAt | fsDate: 'dateTime') } }}</span>
                }
              </p>
            </section>

            @if (isOwner()) {
              <section class="fs-card card" data-testid="baskets.detail.requests">
                <h2 class="card__title"><i class="pi pi-inbox" aria-hidden="true"></i> {{ 'baskets.detail.requests' | t }}</h2>
                @if (b.requests?.length) {
                  <ul class="requests">
                    @for (request of b.requests; track request.requester.id) {
                      <li class="request" data-testid="baskets.detail.requestRow" [attr.data-requester-id]="request.requester.id">
                        <div class="request__head">
                          <fs-user-chip
                            [name]="request.requester.displayName"
                            [userId]="request.requester.id"
                            [avatarUrl]="request.requester.avatarUrl"
                            [verified]="request.requester.verified"
                            [subtitle]="'baskets.detail.requestedAt' | t: { date: (request.requestedAt | fsDate: 'relative') }"
                          />
                          <span class="tag" [attr.data-status]="request.status" data-testid="baskets.detail.requestStatus">{{ 'baskets.request.' + request.status | t }}</span>
                        </div>
                        @if (request.message) {
                          <p class="request__message" data-testid="baskets.detail.requestMessage">{{ request.message }}</p>
                        }
                        <div class="request__actions">
                          @for (answer of answers(request); track answer) {
                            <p-button
                              [label]="'baskets.answer.' + answer | t"
                              [icon]="answerIcon[answer]"
                              size="small"
                              [severity]="answer === picked ? 'primary' : 'secondary'"
                              [outlined]="answer !== picked"
                              [loading]="answering() === request.requester.id + answer"
                              (onClick)="answerRequest(request, answer)"
                              [attr.data-testid]="'baskets.detail.answer.' + answer"
                            />
                          }
                        </div>
                      </li>
                    }
                  </ul>
                } @else {
                  <p class="muted" data-testid="baskets.detail.requestsEmpty">{{ 'baskets.detail.requestsEmpty' | t }}</p>
                }
              </section>
            }
          </div>

          <aside class="stack">
            <section class="fs-card card" data-testid="baskets.detail.owner">
              <fs-user-chip
                [name]="b.owner.displayName"
                [userId]="b.owner.id"
                [avatarUrl]="b.owner.avatarUrl"
                [verified]="b.owner.verified"
                [sleepMode]="b.owner.sleepMode"
                [sleepMessage]="b.owner.sleepMessage"
                [link]="['/profile', b.owner.id]"
                size="md"
              />
              @if (!isOwner()) {
                <h2 class="sub">{{ 'baskets.detail.contact' | t }}</h2>
                <div class="contact">
                  @if (b.contact.message) {
                    <p-button [label]="'baskets.detail.message' | t" icon="pi pi-comments" severity="secondary" [outlined]="true" [loading]="message.busy()" (onClick)="message.run(b.owner.id)" data-testid="baskets.detail.message" />
                  }
                  @if (b.contact.phone) {
                    <fs-phone-button [phone]="b.contact.phone" [label]="'baskets.detail.phone' | t" testId="baskets.detail.phone" />
                  }
                  @if (b.contact.mobile) {
                    <fs-phone-button [phone]="b.contact.mobile" [label]="'baskets.detail.mobile' | t" testId="baskets.detail.mobile" />
                  }
                </div>

                @if (b.myRequest; as mine) {
                  <div class="mine" data-testid="baskets.detail.myRequest" [attr.data-status]="mine.status">
                    <p class="mine__status"><i class="pi pi-send" aria-hidden="true"></i> {{ 'baskets.detail.myRequest' | t: { status: ('baskets.request.' + mine.status | t) } }}</p>
                    <p class="muted">{{ 'baskets.detail.myRequestSent' | t: { date: (mine.requestedAt | fsDate: 'dateTime') } }}</p>
                    @if (openRequest(mine)) {
                      <p-button [label]="'baskets.detail.withdraw' | t" icon="pi pi-undo" severity="secondary" [outlined]="true" [loading]="withdraw.busy()" (onClick)="withdraw.run()" data-testid="baskets.detail.withdraw" />
                    }
                  </div>
                }
                @if (b.permissions.mayRequest) {
                  <p-button [label]="'baskets.detail.request' | t" icon="pi pi-send" (onClick)="requestOpen.set(true)" data-testid="baskets.detail.request" />
                }
              }
            </section>

            <section class="fs-card card" data-testid="baskets.detail.location">
              <h2 class="card__title"><i class="pi pi-map-marker" aria-hidden="true"></i> {{ 'baskets.detail.location' | t }}</h2>
              <p class="muted">{{ (b.locationType === 'HOME' && !isOwner() ? 'baskets.detail.locationHome' : 'baskets.detail.locationCustom') | t }}</p>
              <fs-map-view
                [markers]="markers()"
                [center]="center()"
                [zoom]="15"
                [fitMarkers]="false"
                height="16rem"
                [ariaLabel]="'baskets.detail.mapLabel' | t"
                testId="baskets.detail.map"
              />
            </section>
          </aside>
        </div>

        <p-dialog
          [visible]="requestOpen()"
          (visibleChange)="requestOpen.set($event)"
          [modal]="true"
          [draggable]="false"
          [header]="'baskets.requestDialog.title' | t"
          [style]="{ width: 'min(34rem, 94vw)' }"
          data-testid="baskets.requestDialog"
        >
          <p class="dialog-text">{{ 'baskets.requestDialog.text' | t }}</p>
          @if (b.contact.message) {
            <label class="label" for="basket-request-message">{{ 'baskets.requestDialog.message' | t }}</label>
            <textarea
              pTextarea
              id="basket-request-message"
              rows="4"
              [maxlength]="messageMax"
              [ngModel]="requestText()"
              (ngModelChange)="requestText.set($event)"
              aria-describedby="basket-request-hint"
              data-testid="baskets.requestDialog.message"
            ></textarea>
            <small class="hint" id="basket-request-hint">
              {{ 'baskets.requestDialog.messageHint' | t }} {{ 'baskets.requestDialog.count' | t: { count: requestText().length, max: messageMax } }}
            </small>
          } @else {
            <p class="muted" data-testid="baskets.requestDialog.noMessage">{{ 'baskets.requestDialog.noMessage' | t }}</p>
          }
          <ng-template #footer>
            <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="requestOpen.set(false)" data-testid="baskets.requestDialog.cancel" />
            <p-button [label]="'baskets.requestDialog.send' | t" icon="pi pi-send" [loading]="request.busy()" (onClick)="request.run()" data-testid="baskets.requestDialog.send" />
          </ng-template>
        </p-dialog>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .cols { display: grid; gap: 1.25rem; }
    @media (min-width: 60rem) { .cols { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); align-items: start; } }
    .stack { display: grid; gap: 1.25rem; min-width: 0; }
    .card { display: grid; gap: 0.75rem; min-width: 0; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .sub { margin: 0.25rem 0 0; font-size: 0.9rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .photo { width: 100%; max-width: 28rem; height: auto; aspect-ratio: 1; object-fit: cover; border-radius: var(--fs-radius-md); }
    .description { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
    .status {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .chips { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .chip { padding: 0.2rem 0.65rem; border-radius: 999px; background: var(--fs-primary-soft); color: var(--fs-text); font-size: 0.85rem; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.3rem 1rem; margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; }
    .requests { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; }
    .request { display: grid; gap: 0.5rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--fs-border); }
    .request:last-child { padding-bottom: 0; border-bottom: 0; }
    .request__head { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; justify-content: space-between; }
    .request__message { margin: 0; padding: 0.5rem 0.75rem; border-left: 3px solid var(--fs-primary); background: var(--fs-surface-muted); white-space: pre-line; overflow-wrap: anywhere; }
    .request__actions { display: flex; flex-wrap: wrap; gap: 0.4rem; }
    .tag { padding: 0.1rem 0.55rem; border-radius: 999px; background: var(--fs-surface-muted); font-size: 0.82rem; font-weight: 600; }
    .tag[data-status='PICKED_UP'] { background: var(--fs-primary-soft); }
    .tag[data-status='DENIED'], .tag[data-status='NOT_PICKED_UP'] { background: var(--fs-danger-soft); }
    .contact { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .mine { display: grid; gap: 0.35rem; padding: 0.75rem; border-radius: var(--fs-radius-md); background: var(--fs-surface-muted); }
    .mine__status { margin: 0; font-weight: 650; }
    .dialog-text { margin: 0 0 0.75rem; }
    .label { display: block; margin-bottom: 0.35rem; font-weight: 650; font-size: 0.9rem; }
    textarea { width: 100%; }
    .hint { display: block; margin-top: 0.3rem; color: var(--fs-text-muted); }
  `,
})
export class BasketPageComponent {
  private readonly api = inject(BasketsService);
  private readonly messaging = inject(MessagingService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly active = BasketStatus.Active;
  protected readonly picked = BasketRequestAnswerStatusEnum.PickedUp;
  protected readonly messageMax = REQUEST_MESSAGE_MAX;
  protected readonly answerIcon: Record<BasketRequestAnswerStatusEnum, string> = {
    PICKED_UP: 'pi pi-check',
    DENIED: 'pi pi-times',
    NOT_PICKED_UP: 'pi pi-ban',
  };
  protected openRequest(request: BasketRequest): boolean {
    return isOpenRequest(request.status);
  }

  protected readonly basketId = toSignal(this.route.paramMap.pipe(map((params) => params.get('basketId') ?? undefined)));

  protected readonly basket = loadResource<Basket, string>({
    params: () => this.basketId(),
    loader: (basketId) => this.api.getBasket({ basketId }),
    isEmpty: () => false,
  });

  protected readonly isOwner = computed(() => {
    const b = this.basket.data();
    return !!b && b.owner.id === this.session.user()?.id;
  });
  protected readonly photo = computed(() => photoVariant(this.basket.data()?.photoUrl, 'lg'));
  protected readonly center = computed<[number, number] | undefined>(() => {
    const b = this.basket.data();
    return b ? [b.lat, b.lon] : undefined;
  });
  protected readonly markers = computed<MapMarker[]>(() => {
    const b = this.basket.data();
    return b ? [{ id: b.id, kind: 'basket', lat: b.lat, lon: b.lon, label: b.description }] : [];
  });

  // --- request (non-owners) ---
  /** Request dialog visibility. */
  protected readonly requestOpen = signal(false);
  protected readonly requestText = signal('');
  protected readonly request = runMutation(
    () => {
      const message = this.requestText().trim();
      return this.api.requestBasket({
        basketId: this.basketId()!,
        basketRequestCreate: this.basket.data()?.contact.message && message ? { message } : {},
      });
    },
    {
      successToastKey: 'baskets.toast.requested',
      onSuccess: () => {
        this.requestOpen.set(false);
        this.requestText.set('');
        this.basket.reload();
      },
    },
  );
  protected readonly withdraw = runMutation(() => this.api.withdrawBasketRequest({ basketId: this.basketId()! }), {
    successToastKey: 'baskets.toast.withdrawn',
    onSuccess: () => this.basket.reload(),
  });

  // --- owner ---
  protected readonly answering = signal<string | null>(null);
  private readonly answer = runMutation(
    (requesterId: string, status: BasketRequestAnswerStatusEnum) =>
      this.api.answerBasketRequest({ basketId: this.basketId()!, requesterId, basketRequestAnswer: { status } }),
    {
      successToastKey: 'baskets.toast.answered',
      onSuccess: () => {
        this.answering.set(null);
        this.basket.reload();
      },
      onError: () => this.answering.set(null),
    },
  );
  protected readonly remove = runMutation(() => this.api.deleteBasket({ basketId: this.basketId()! }), {
    successToastKey: 'baskets.toast.deleted',
    onSuccess: () => void this.router.navigate(['/baskets/mine']),
  });

  // --- message: find or create the direct conversation, then open it (no /messages/new?to=) ---
  protected readonly message = runMutation(
    (ownerId: string) => this.messaging.lookupConversation({ conversationLookup: { memberIds: [ownerId] } }),
    { onSuccess: (conversation: Conversation) => void this.router.navigate(['/messages', conversation.id]) },
  );

  protected answers(request: BasketRequest): BasketRequestAnswerStatusEnum[] {
    return answerActions(request.status);
  }

  protected answerRequest(request: BasketRequest, status: BasketRequestAnswerStatusEnum): void {
    this.answering.set(request.requester.id + status);
    void this.answer.run(request.requester.id, status);
  }

  protected confirmDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('baskets.detail.deleteTitle'),
      message: this.i18n.t('baskets.detail.deleteText'),
      acceptLabel: this.i18n.t('baskets.detail.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }
}
