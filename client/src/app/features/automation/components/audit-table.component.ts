import { ChangeDetectionStrategy, Component, computed, effect, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Observable } from 'rxjs';

import { AuditKind, AutomationAudit, AutomationAuditPage } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
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
 * reasons (rule chip + text), user message, dry-run tag, "load more" paging. Used by the store and region pages.
 */
@Component({
  selector: 'fs-automation-audit-table',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, InputText, RouterLink, TPipe, UserChipComponent],
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
      <div class="row">
        <label class="field">
          <span class="muted">{{ 'automation.common.from' | t }}</span>
          <input pInputText type="date" [ngModel]="from()" (ngModelChange)="from.set($event || undefined)" [attr.data-testid]="testId() + '.from'" />
        </label>
        <label class="field">
          <span class="muted">{{ 'automation.common.to' | t }}</span>
          <input pInputText type="date" [ngModel]="to()" (ngModelChange)="to.set($event || undefined)" [attr.data-testid]="testId() + '.to'" />
        </label>
      </div>
    </div>

    <fs-data-state [state]="first" skeleton="table" [emptyTitle]="'automation.audits.empty' | t" [testId]="testId() + '.state'" [attr.data-testid]="testId() + '.state'">
      <ng-template [fsData]="first">
        <ul class="list" [attr.data-testid]="testId() + '.list'">
          @for (audit of items(); track audit.id) {
            <li class="fs-card audit" [attr.data-testid]="testId() + '.row'" [attr.data-kind]="audit.kind" [attr.data-status]="audit.status" [attr.data-dry-run]="audit.dryRun">
              <button type="button" class="audit__head" [attr.aria-expanded]="open().has(audit.id)" (click)="toggle(audit.id)" [attr.data-testid]="testId() + '.toggle'">
                <span [class]="'badge badge--' + tone(audit.status)" [attr.data-testid]="testId() + '.status'">{{ statusLabel(audit) | t }}</span>
                @if (audit.dryRun) {
                  <span class="badge badge--dry" [attr.data-testid]="testId() + '.dryRun'">{{ 'automation.common.dryRunTag' | t }}</span>
                }
                <span class="badge">{{ 'automation.kind.' + audit.kind | t }}</span>
                <span class="grow audit__title">
                  @if (showStore()) {
                    <strong>{{ audit.storeName }}</strong> ·
                  }
                  {{ audit.user?.displayName ?? '–' }}
                  @if (audit.pickupAt) {
                    · {{ audit.pickupAt | fsDate: 'dateTime' }}
                  }
                </span>
                <span class="muted">{{ audit.createdAt | fsDate: 'relative' }}</span>
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
                  <span class="muted">{{ audit.createdAt | fsDate: 'dateTimeLong' }}</span>
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
    .audit__title { overflow-wrap: anywhere; }
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
