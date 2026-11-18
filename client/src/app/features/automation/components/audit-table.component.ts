import { ChangeDetectionStrategy, Component, computed, effect, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { Observable } from 'rxjs';

import { AuditKind, AutomationAudit, AutomationAuditPage } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { DATE_INPUT_IMPORTS } from '../../../shared/date-input';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { decisionTone } from '../automation.util';

export interface AuditQuery {
  kind?: AuditKind;
  excludeConfirmed: boolean;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export type AuditLoader = (query: AuditQuery) => Observable<AutomationAuditPage>;

export const AUDIT_PAGE_SIZE = 25;
const KINDS: AuditKind[] = [AuditKind.Pickup, AuditKind.Request, AuditKind.Advert, AuditKind.Cancellation, AuditKind.ExtraSlot];

/**
 * `fs-automation-audit-table` (AU8): kind filter chips, "exclude confirmed", date range, decision badges, expandable
 * reasons (rule chip + text), user message, "load more" paging. Used by the store and region pages.
 */
@Component({
  selector: 'fs-automation-audit-table',
  imports: [...DATE_INPUT_IMPORTS, ...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="filters">
      @if (showKinds()) {
      <ul class="chips" [attr.aria-label]="'automation.audits.kind' | t">
        <li>
          <button type="button" class="chip" [class.chip--on]="!kind()" [attr.aria-pressed]="!kind()" (click)="kind.set(undefined)" [attr.data-testid]="testId() + '.kind.ALL'">
            {{ 'automation.audits.allKinds' | t }}
          </button>
        </li>
        @for (k of kinds; track k) {
          <li>
            <button type="button" class="chip" [class.chip--on]="kind() === k" [attr.aria-pressed]="kind() === k" (click)="kind.set(k)" [attr.data-testid]="testId() + '.kind.' + k">
              {{ 'automation.kind.' + k | t }}
            </button>
          </li>
        }
      </ul>
      }
      <div class="row">
        <p-checkbox inputId="audit-exclude" [binary]="true" [ngModel]="excludeConfirmed()" (ngModelChange)="excludeConfirmed.set($event)" [attr.data-testid]="testId() + '.excludeConfirmed'" />
        <label for="audit-exclude">{{ 'automation.audits.excludeConfirmed' | t }}</label>
      </div>
      <div class="range">
        <label class="field">
          <span class="muted">{{ 'automation.common.from' | t }}</span>
          <fs-date-input [ngModel]="from()" (ngModelChange)="from.set($event || undefined)" [testId]="testId() + '.from'" />
        </label>
        <label class="field">
          <span class="muted">{{ 'automation.common.to' | t }}</span>
          <fs-date-input [ngModel]="to()" (ngModelChange)="to.set($event || undefined)" [testId]="testId() + '.to'" />
        </label>
      </div>
    </div>

    <fs-data-state [state]="first" skeleton="table" [emptyTitle]="'automation.audits.empty' | t" [testId]="testId() + '.state'" [attr.data-testid]="testId() + '.state'">
      <ng-template [fsData]="first">
        <ul class="list" [attr.data-testid]="testId() + '.list'">
          @for (audit of items(); track audit.id) {
            <li class="fs-card audit" [attr.data-testid]="testId() + '.row'" [attr.data-kind]="audit.kind" [attr.data-status]="audit.status">
              <button type="button" class="audit__head" [attr.aria-expanded]="open().has(audit.id)" (click)="toggle(audit.id)" [attr.data-testid]="testId() + '.toggle'">
                <span [class]="'badge badge--' + tone(audit.status)" [attr.data-testid]="testId() + '.status'">{{ statusLabel(audit) | t }}</span>
                <span class="badge">{{ 'automation.kind.' + audit.kind | t }}</span>
                <!-- parts as separate items: the "·" opens the next part and never ends a line; no "–" for a missing person (design review B13) -->
                <span class="grow audit__title">
                  @if (showStore()) {
                    <strong class="audit__part">{{ audit.storeName }}</strong>
                  }
                  @if (audit.user) {
                    <span class="audit__part">{{ audit.user.displayName }}</span>
                  }
                  @if (audit.pickupAt) {
                    <span class="audit__part audit__part--date">{{ audit.pickupAt | fsDate: 'dateTime' }}</span>
                  }
                  @if (!showStore() && !audit.user && !audit.pickupAt) {
                    <span class="audit__part">–</span>
                  }
                </span>
                <time class="muted" [attr.datetime]="audit.createdAt" [title]="audit.createdAt | fsDate: 'dateTime'">{{ audit.createdAt | fsDate: 'relative' }}</time>
                <i [class]="open().has(audit.id) ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" aria-hidden="true"></i>
              </button>
              @if (open().has(audit.id)) {
                <div class="audit__body" [attr.data-testid]="testId() + '.details'">
                  @if (audit.user; as u) {
                    <fs-user-chip [name]="u.displayName" [avatarUrl]="u.avatarUrl" [userId]="u.id" [link]="['/users', u.id]" />
                  }
                  @if (showStore()) {
                    <a [routerLink]="['/stores', audit.storeId, 'automation', 'audits']">{{ audit.storeName }}</a>
                  }
                  <ul class="list">
                    @for (reason of audit.reasons; track $index) {
                      <li class="row" [attr.data-testid]="testId() + '.reason'" [attr.data-rule]="reason.rule">
                        <span class="badge">{{ 'automation.rule.' + reason.rule | t }}</span><span>{{ reason.text }}</span>
                      </li>
                    }
                  </ul>
                  @if (audit.userMessage) {
                    <p class="preview" [attr.data-testid]="testId() + '.message'">{{ audit.userMessage }}</p>
                  }
                  @if (audit.error) {
                    <p class="error-text">{{ audit.error }}</p>
                  }
                  <span class="muted">{{ audit.createdAt | fsDate: 'dateTime' }}</span>
                </div>
              }
            </li>
          }
        </ul>
        @if (hasMore()) {
          <div class="actions more">
            <p-button [label]="'automation.audits.more' | t" icon="pi pi-angle-down" [text]="true" [loading]="more.busy()" (onClick)="more.run()" [attr.data-testid]="testId() + '.more'" />
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../automation.scss',
  styles: `
    .filters { display: grid; gap: 0.6rem; }
    .audit { padding: 0; }
    .audit__head {
      display: flex; flex-wrap: wrap; gap: 0.4rem 0.6rem; align-items: center; width: 100%;
      padding: 0.6rem 0.8rem; border: 0; background: none; color: inherit; font: inherit; text-align: left; cursor: pointer;
    }
    .audit__title { display: flex; flex-wrap: wrap; column-gap: 0.35rem; min-width: 0; overflow-wrap: anywhere; }
    .audit__part + .audit__part::before { content: '·'; margin-inline-end: 0.35rem; color: var(--fs-text-muted); font-weight: 400; }
    .audit__part--date { white-space: nowrap; }
    /* date range: "from" and "to" side by side, each as wide as its half (design review B12, C5) */
    .range { display: grid; grid-template-columns: repeat(2, minmax(0, 12rem)); gap: 0.6rem; }
    .range fs-date-input { width: 100%; }
    /* phones: badges, time and chevron on the first line, the title across the full width below, so every row has
       the same shape (design review C5, B13) */
    @media (max-width: 39.99rem) {
      .range { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .audit__head { display: grid; grid-template-columns: auto auto minmax(0, 1fr) auto; column-gap: 0.4rem; row-gap: 0.3rem; }
      .audit__head time { justify-self: end; }
      .audit__title { grid-column: 1 / -1; grid-row: 2; }
    }
    .audit__body { display: grid; gap: 0.5rem; padding: 0 0.8rem 0.8rem; }
    .more { justify-content: center; }
  `,
})
export class AuditTableComponent {
  readonly loader = input.required<AuditLoader>();
  readonly showStore = input(false);
  readonly showKinds = input(true);
  readonly testId = input('automation.audits');

  protected readonly kinds = KINDS;
  protected readonly kind = signal<AuditKind | undefined>(undefined);
  protected readonly excludeConfirmed = signal(false);
  protected readonly from = signal<string | undefined>(undefined);
  protected readonly to = signal<string | undefined>(undefined);
  protected readonly open = signal<ReadonlySet<string>>(new Set());
  private readonly extra = signal<AutomationAudit[]>([]);
  private readonly nextPage = signal(1);

  private readonly query = computed(() => ({
    loader: this.loader(),
    kind: this.kind(),
    excludeConfirmed: this.excludeConfirmed(),
    from: this.from(),
    to: this.to(),
  }));

  protected readonly first = loadResource<AutomationAuditPage, ReturnType<typeof this.query>>({
    params: () => this.query(),
    loader: ({ loader, ...filters }) => loader({ ...filters, page: 0, size: AUDIT_PAGE_SIZE }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly items = computed(() => [...(this.first.data()?.items ?? []), ...this.extra()]);
  protected readonly hasMore = computed(() => {
    const meta = this.first.data()?.page;
    return !!meta && this.items().length < meta.totalItems;
  });

  constructor() {
    effect(() => {
      this.first.data();
      untracked(() => {
        this.extra.set([]);
        this.nextPage.set(1);
      });
    });
  }

  protected readonly more = runMutation(
    () => {
      const { loader, ...filters } = this.query();
      return loader({ ...filters, page: this.nextPage(), size: AUDIT_PAGE_SIZE });
    },
    {
      onSuccess: (page) => {
        this.extra.update((list) => [...list, ...page.items]);
        this.nextPage.update((n) => n + 1);
      },
    },
  );

  protected tone = decisionTone;

  protected statusLabel(audit: AutomationAudit): string {
    // decision 167 S2: a declined sign-up was blocked by the rules ("Gesperrt"); request declines keep "Abgelehnt"
    if (audit.kind === AuditKind.Pickup && audit.status === 'DECLINED') return 'automation.decision.BLOCKED';
    return `automation.decision.${audit.status}`;
  }

  protected toggle(id: string): void {
    this.open.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }
}
