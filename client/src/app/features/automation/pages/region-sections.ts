import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  AutomationService,
  CleaningExemption,
  Holiday,
  MetricSeries,
  PickupCountGroup,
  RegionAutomationOverview,
  TelegramChat,
  TelegramChatConfig,
  TelegramChatConfigList,
  TelegramService,
  UserSummary,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { AutomationAuditLoaders } from '../automation.resource';
import { addDays, fillDays, isoDate } from '../automation.util';
import { AuditLoader, AuditTableComponent } from '../components/audit-table.component';
import { ChartSeries, DailyLineChartComponent } from '../components/daily-line-chart.component';
import { RegionAutomationContext } from '../region-automation.context';

/** Overview: store × pickup policy / dry run / auto-accept / adverts / alerts with links (AU1–AU5). */
@Component({
  selector: 'fs-region-automation-overview',
  imports: [...DATA_STATE_IMPORTS, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.region.section.overview' | t }}</h2>
      <fs-data-state [state]="overview" skeleton="table" [emptyTitle]="'automation.region.noStores' | t" testId="automation.region.overview" data-testid="automation.region.overview">
        <ng-template [fsData]="overview" let-o>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ 'automation.region.store' | t }}</th>
                  <th>{{ 'automation.section.policy' | t }}</th>
                  <th>{{ 'automation.section.auto-accept' | t }}</th>
                  <th>{{ 'automation.section.adverts' | t }}</th>
                  <th>{{ 'automation.section.signout-alerts' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of o.stores; track row.store.id) {
                  <tr data-testid="automation.region.overviewRow" [attr.data-store]="row.store.name">
                    <td><a [routerLink]="['/stores', row.store.id, 'automation']">{{ row.store.name }}</a></td>
                    <td>
                      @if (row.pickupPolicyEnabled) {
                        <span [class]="row.dryRun ? 'badge badge--dry' : 'badge badge--ok'">{{ (row.dryRun ? 'automation.common.dryRunTag' : 'automation.common.live') | t }}</span>
                      } @else {
                        <span class="muted">–</span>
                      }
                    </td>
                    <td>{{ (row.requestPolicyEnabled ? 'automation.common.on' : 'automation.common.off') | t }}</td>
                    <td>{{ row.advertCount }}</td>
                    <td>{{ (row.signoutAlertEnabled ? 'automation.common.on' : 'automation.common.off') | t }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class RegionOverviewSection {
  private readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(AutomationService);
  protected readonly overview = loadResource<RegionAutomationOverview, string>({
    params: () => this.ctx.regionId(),
    loader: (regionId) => this.api.getRegionAutomation({ regionId }),
    isEmpty: (o) => o.stores.length === 0,
  });
}

/** All decisions of the region (AU8): coordinators and CAN_SEE_ALL_AUTOMATION_DECISIONS see all, members their own stores. */
@Component({
  selector: 'fs-region-automation-audits',
  imports: [AuditTableComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.audits.title' | t }}</h2>
      <fs-automation-audit-table [loader]="loader" [showStore]="true" testId="automation.region.audits" />
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class RegionAuditsSection {
  private readonly ctx = inject(RegionAutomationContext);
  protected readonly loader: AuditLoader = inject(AutomationAuditLoaders).region(() => this.ctx.regionId());
}

/** Cleaning rule exemptions of the region (AU1): user search + reason, remove. */
@Component({
  selector: 'fs-region-cleaning-exemptions',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.region.section.cleaning-exemptions' | t }}</h2>
      <p class="muted">{{ 'automation.exemptions.intro' | t }}</p>
      <div class="row add">
        @if (user(); as u) {
          <fs-user-chip [name]="u.displayName" [avatarUrl]="u.avatarUrl" [userId]="u.id" />
          <p-button icon="pi pi-times" [text]="true" size="small" [ariaLabel]="'automation.common.remove' | t" (onClick)="user.set(null)" />
        } @else {
          <fs-user-picker [regionId]="ctx.regionId()" (picked)="user.set($event)" testId="automation.exemptions.user" />
        }
        <input pInputText class="grow" [placeholder]="'automation.exemptions.reason' | t" [attr.aria-label]="'automation.exemptions.reason' | t" [(ngModel)]="reason" maxlength="500" data-testid="automation.exemptions.reason" />
        <p-button [label]="'automation.exemptions.add' | t" icon="pi pi-plus" [disabled]="!user()" [loading]="add.busy()" (onClick)="add.run()" data-testid="automation.exemptions.add" />
      </div>
      <fs-data-state [state]="list" skeleton="list" [emptyTitle]="'automation.exemptions.empty' | t" testId="automation.exemptions.state" data-testid="automation.exemptions.state">
        <ng-template [fsData]="list" let-rows>
          <ul class="list">
            @for (row of rows; track row.id) {
              <li class="row" data-testid="automation.exemptions.row" [attr.data-user]="row.user.displayName">
                <fs-user-chip [name]="row.user.displayName" [avatarUrl]="row.user.avatarUrl" [userId]="row.user.id" />
                <span class="grow muted">{{ row.reason }}</span>
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small" [ariaLabel]="'automation.common.remove' | t" [loading]="remove.busy()" (onClick)="remove.run(row)" data-testid="automation.exemptions.remove" />
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
  styles: `.add { align-items: flex-start; }`,
})
export class RegionExemptionsSection {
  protected readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(AutomationService);
  protected readonly user = signal<UserSummary | null>(null);
  protected readonly reason = signal('');

  protected readonly list = loadResource<CleaningExemption[], string>({
    params: () => this.ctx.regionId(),
    loader: (regionId) => this.api.listCleaningExemptions({ regionId }),
  });

  protected readonly add = runMutation(
    () =>
      this.api.addCleaningExemption({
        regionId: this.ctx.regionId()!,
        cleaningExemptionCreate: { userId: this.user()!.id, reason: this.reason().trim() || undefined },
      }),
    {
      successToastKey: 'automation.exemptions.added',
      onSuccess: () => {
        this.user.set(null);
        this.reason.set('');
        this.list.reload();
      },
    },
  );

  protected readonly remove = runMutation(
    (row: CleaningExemption) => this.api.removeCleaningExemption({ regionId: this.ctx.regionId()!, exemptionId: row.id }),
    { successToastKey: 'automation.exemptions.removed', onSuccess: () => this.list.reload() },
  );
}

/** Future pickup users grouped by pickup count (AU7, CAN_SEE_USER_PICKUP_COUNT_GROUPING or region admins). */
@Component({
  selector: 'fs-region-pickup-grouping',
  imports: [...DATA_STATE_IMPORTS, FormsModule, RouterLink, Select, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <div class="row">
        <h2 class="grow">{{ 'automation.region.section.grouping' | t }}</h2>
        <label for="grp-days" class="muted">{{ 'automation.grouping.days' | t }}</label>
        <p-select inputId="grp-days" [options]="dayOptions" [ngModel]="days()" (ngModelChange)="days.set($event)" data-testid="automation.grouping.days" />
      </div>
      <p class="muted">{{ 'automation.grouping.intro' | t: { days: days() } }}</p>
      <fs-data-state [state]="groups" skeleton="list" [emptyTitle]="'automation.grouping.empty' | t" testId="automation.grouping.state" data-testid="automation.grouping.state" [askWho]="'automation.grouping.askWho' | t">
        <ng-template [fsData]="groups" let-rows>
          @for (group of rows; track group.pickupCount) {
            <section class="group" data-testid="automation.grouping.group" [attr.data-count]="group.pickupCount">
              <h3>{{ 'automation.grouping.count' | t: { count: group.pickupCount } }} <span class="muted">({{ group.users.length }})</span></h3>
              <ul class="list">
                @for (entry of group.users; track entry.user.id) {
                  <li class="row" data-testid="automation.grouping.user" [attr.data-user]="entry.user.displayName">
                    <fs-user-chip [name]="entry.user.displayName" [avatarUrl]="entry.user.avatarUrl" [userId]="entry.user.id" [link]="['/users', entry.user.id]" />
                    <span class="chips">
                      @for (store of entry.stores; track store.id) {
                        <a class="chip" [routerLink]="['/stores', store.id]">{{ store.name }}</a>
                      }
                    </span>
                  </li>
                }
              </ul>
            </section>
          }
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
  styles: `.group { display: grid; gap: 0.4rem; padding: 0.6rem 0; border-top: 1px solid var(--fs-border); } a.chip { text-decoration: none; }`,
})
export class RegionGroupingSection {
  private readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(AutomationService);
  protected readonly dayOptions = [7, 14, 30];
  protected readonly days = signal(14);

  protected readonly groups = loadResource<PickupCountGroup[], { regionId: string; days: number }>({
    params: () => {
      const regionId = this.ctx.regionId();
      return regionId ? { regionId, days: this.days() } : undefined;
    },
    loader: (p) => this.api.getPickupCountGrouping(p),
  });
}

/** Daily anonymous counts (AU9): range, key multiselect, line chart. */
@Component({
  selector: 'fs-region-metrics',
  imports: [...DATA_STATE_IMPORTS, DailyLineChartComponent, FormsModule, InputText, MultiSelect, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.region.section.metrics' | t }}</h2>
      <div class="row">
        <label class="field">
          <span class="muted">{{ 'automation.common.from' | t }}</span>
          <input pInputText type="date" [ngModel]="from()" (ngModelChange)="$event && from.set($event)" data-testid="automation.metrics.from" />
        </label>
        <label class="field">
          <span class="muted">{{ 'automation.common.to' | t }}</span>
          <input pInputText type="date" [ngModel]="to()" (ngModelChange)="$event && to.set($event)" data-testid="automation.metrics.to" />
        </label>
        <div class="field grow">
          <label for="metric-keys" class="muted">{{ 'automation.metrics.keys' | t }}</label>
          <p-multiselect inputId="metric-keys" [options]="keyOptions()" optionLabel="label" optionValue="key" [ngModel]="selected()" (ngModelChange)="selected.set($event)" display="chip" [placeholder]="'automation.metrics.allKeys' | t" data-testid="automation.metrics.keys" />
        </div>
      </div>
      <fs-data-state [state]="metrics" skeleton="card" [emptyTitle]="'automation.metrics.empty' | t" testId="automation.metrics.state" data-testid="automation.metrics.state">
        <ng-template [fsData]="metrics">
          <fs-daily-line-chart [series]="series()" [label]="'automation.region.section.metrics' | t" testId="automation.metrics.chart" />
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
  styles: `p-multiselect { width: 100%; }`,
})
export class RegionMetricsSection {
  private readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly i18n = inject(I18nService);
  protected readonly to = signal(isoDate(new Date()));
  protected readonly from = signal(addDays(isoDate(new Date()), -89));
  protected readonly selected = signal<string[]>([]);

  protected readonly metrics = loadResource<MetricSeries[], { regionId: string; from: string; to: string }>({
    params: () => {
      const regionId = this.ctx.regionId();
      return regionId ? { regionId, from: this.from(), to: this.to() } : undefined;
    },
    loader: (p) => this.api.getAutomationMetrics(p),
    isEmpty: (list) => list.every((s) => s.points.length === 0),
  });

  protected readonly keyOptions = computed(() =>
    (this.metrics.data() ?? []).map((s) => ({ key: s.key, label: this.keyLabel(s.key) })),
  );

  protected readonly series = computed<ChartSeries[]>(() => {
    const chosen = this.selected();
    return (this.metrics.data() ?? [])
      .filter((s) => chosen.length === 0 || chosen.includes(s.key))
      .map((s) => ({ key: s.key, label: this.keyLabel(s.key), points: fillDays(s.points, this.from(), this.to()) }));
  });

  private keyLabel(key: string): string {
    const i18nKey = `automation.metric.${key.replace('.', '_')}`;
    return this.i18n.has(i18nKey) ? this.i18n.t(i18nKey) : key;
  }
}

/** Region Telegram alert chats (AU10): pick from chats the bot has seen, enable, send a test message. */
@Component({
  selector: 'fs-region-telegram-chats',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, Select, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.region.section.telegram' | t }}</h2>
      <p class="muted">{{ 'automation.telegram.intro' | t }}</p>
      <fs-data-state [state]="config" skeleton="list" testId="automation.telegram.state" data-testid="automation.telegram.state">
        <ng-template [fsData]="config">
          <ul class="list">
            @for (chat of draft(); track chat.chatId) {
              <li class="row" data-testid="automation.telegram.chat" [attr.data-chat-id]="chat.chatId">
                <span class="grow"><strong>{{ chat.title }}</strong> <span class="muted">{{ chat.chatId }}</span></span>
                <p-toggleswitch [inputId]="'tg-' + chat.chatId" [(ngModel)]="chat.enabled" [ariaLabel]="'automation.telegram.enabled' | t" data-testid="automation.telegram.enabled" />
                <p-button [label]="'automation.telegram.test' | t" icon="pi pi-send" [text]="true" size="small" [loading]="test.busy()" (onClick)="test.run(chat)" data-testid="automation.telegram.test" />
                <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small" [ariaLabel]="'automation.common.remove' | t" (onClick)="removeChat(chat)" />
              </li>
            } @empty {
              <li class="muted">{{ 'automation.telegram.none' | t }}</li>
            }
          </ul>
          <div class="row">
            <p-select
              [options]="available()"
              optionLabel="title"
              [ngModel]="pick()"
              (ngModelChange)="pick.set($event)"
              [placeholder]="'automation.telegram.pick' | t"
              [loading]="seen.status() === 'loading'"
              [emptyMessage]="'automation.telegram.noneSeen' | t"
              data-testid="automation.telegram.pick"
            />
            <p-button [label]="'automation.telegram.add' | t" icon="pi pi-plus" [outlined]="true" [disabled]="!pick()" (onClick)="addChat()" data-testid="automation.telegram.add" />
          </div>
          <div class="actions">
            <p-button [label]="'common.save' | t" icon="pi pi-save" [disabled]="!dirty()" [loading]="save.busy()" (onClick)="save.run()" data-testid="automation.telegram.save" />
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class RegionTelegramSection {
  private readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(TelegramService);
  protected readonly draft = signal<TelegramChatConfig[]>([]);
  protected readonly pick = signal<TelegramChat | null>(null);

  protected readonly config = loadResource<TelegramChatConfigList, string>({
    params: () => this.ctx.regionId(),
    loader: (regionId) => this.api.getRegionTelegramChats({ regionId }),
    isEmpty: () => false,
  });

  protected readonly seen = loadResource<TelegramChat[]>({
    loader: () => this.api.listTelegramChats(),
    isEmpty: () => false,
  });

  protected readonly available = computed(() => {
    const used = new Set(this.draft().map((c) => c.chatId));
    return (this.seen.data() ?? []).filter((c) => !used.has(c.chatId));
  });

  constructor() {
    // copy the stored configuration into the editable draft whenever it (re)loads
    effect(() => {
      const data = this.config.data();
      untracked(() => this.draft.set((data?.chats ?? []).map((c) => ({ ...c }))));
    });
  }

  protected dirty(): boolean {
    return JSON.stringify(this.draft()) !== JSON.stringify(this.config.data()?.chats ?? []);
  }

  protected addChat(): void {
    const chat = this.pick();
    if (!chat) return;
    this.draft.update((list) => [...list, { chatId: chat.chatId, title: chat.title, enabled: true }]);
    this.pick.set(null);
  }

  protected removeChat(chat: TelegramChatConfig): void {
    this.draft.update((list) => list.filter((c) => c !== chat));
  }

  protected readonly save = runMutation(
    () => this.api.putRegionTelegramChats({ regionId: this.ctx.regionId()!, telegramChatConfigList: { chats: this.draft() } }),
    { successToastKey: 'automation.common.saved', onSuccess: () => this.config.reload() },
  );

  protected readonly test = runMutation(
    (chat: TelegramChatConfig) =>
      this.api.testRegionTelegramChat({ regionId: this.ctx.regionId()!, telegramChatTest: { chatId: chat.chatId } }),
    { successToastKey: 'automation.telegram.testSent' },
  );
}

/** Public holidays of the region's canton (AU1 holiday rule). */
@Component({
  selector: 'fs-region-holidays',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, FormsModule, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <div class="row">
        <h2 class="grow">{{ 'automation.region.section.holidays' | t }}</h2>
        <p-select [options]="years" [ngModel]="year()" (ngModelChange)="year.set($event)" [ariaLabel]="'automation.holidays.year' | t" data-testid="automation.holidays.year" />
      </div>
      <fs-data-state [state]="holidays" skeleton="table" [emptyTitle]="'automation.holidays.empty' | t" testId="automation.holidays.state" data-testid="automation.holidays.state">
        <ng-template [fsData]="holidays" let-rows>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ 'automation.holidays.date' | t }}</th>
                  <th>{{ 'automation.holidays.name' | t }}</th>
                  <th>{{ 'automation.holidays.scope' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track row.date + row.name) {
                  <tr data-testid="automation.holidays.row">
                    <td>{{ row.date | fsDate: 'date' }}</td>
                    <td>{{ row.name }}</td>
                    <td><span class="badge">{{ 'automation.holidays.' + row.scope | t }}</span></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </ng-template>
      </fs-data-state>
    </section>
  `,
  styleUrl: '../automation.scss',
})
export class RegionHolidaysSection {
  private readonly ctx = inject(RegionAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly thisYear = new Date().getFullYear();
  protected readonly years = [this.thisYear - 1, this.thisYear, this.thisYear + 1];
  protected readonly year = signal(this.thisYear);

  protected readonly holidays = loadResource<Holiday[], { regionId: string; year: number }>({
    params: () => {
      const regionId = this.ctx.regionId();
      return regionId ? { regionId, year: this.year() } : undefined;
    },
    loader: (p) => this.api.listRegionHolidays(p),
  });
}
