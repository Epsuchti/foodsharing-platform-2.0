import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  AdvertChannel,
  AutomationRunResult,
  AutomationService,
  OpenSlotAdvert,
  PlannedAdvert,
  TelegramChatConfigList,
  TelegramService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ADVERT_PLACEHOLDERS, advertValues, clone, emptyAdvert, toggle } from '../automation.util';
import { TemplateEditorComponent } from '../components/template-editor.component';
import { StoreAutomationContext } from '../store-automation.context';

export const MAX_ADVERTS = 5;
export const MAX_MESSAGES = 10;
const CHANNELS: AdvertChannel[] = [AdvertChannel.StoreChat, AdvertChannel.Push, AdvertChannel.Telegram];
type MessageList = 'storeMessages' | 'pushMessages' | 'telegramMessages';
const LIST_OF: Record<AdvertChannel, MessageList> = {
  [AdvertChannel.StoreChat]: 'storeMessages',
  [AdvertChannel.Push]: 'pushMessages',
  [AdvertChannel.Telegram]: 'telegramMessages',
};

/**
 * Open-slot adverts (AU4): up to five adverts per store (trigger hours, channels, Telegram chat, rotating messages
 * per channel with placeholders and a preview for the next pickup, late cancellation), planned notifications for the
 * next 7 days and "run now".
 */
@Component({
  selector: 'fs-adverts-section',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, InputNumber, Select, TemplateEditorComponent, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card fs-card">
      <h2>{{ 'automation.adverts.title' | t }}</h2>
      <p class="muted">{{ 'automation.adverts.intro' | t }}</p>
    </section>

    @for (advert of drafts(); track advert.advertNumber) {
      <section class="card fs-card" data-testid="automation.adverts.card" [attr.data-number]="advert.advertNumber" [attr.data-enabled]="advert.enabled">
        <div class="row">
          <h3 class="grow">{{ 'automation.adverts.number' | t: { number: advert.advertNumber! } }}</h3>
          @if (!saved(advert.advertNumber!)) {
            <span class="badge badge--dry">{{ 'automation.adverts.unsaved' | t }}</span>
          }
        </div>
        <div class="form-grid">
          <div class="switch-row">
            <label [for]="'ad-en-' + advert.advertNumber">{{ 'automation.adverts.enabled' | t }}</label>
            <p-toggleswitch [inputId]="'ad-en-' + advert.advertNumber" [(ngModel)]="advert.enabled" [disabled]="!ctx.mayEdit()" data-testid="automation.adverts.enabled" />
          </div>
          <div class="switch-row">
            <label [for]="'ad-dry-' + advert.advertNumber">{{ 'automation.common.dryRun' | t }}</label>
            <p-toggleswitch [inputId]="'ad-dry-' + advert.advertNumber" [(ngModel)]="advert.dryRun" [disabled]="!ctx.mayEdit()" data-testid="automation.adverts.dryRun" />
          </div>
          <div class="field">
            <label [for]="'ad-h-' + advert.advertNumber">{{ 'automation.adverts.trigger' | t }}</label>
            <p-inputnumber [inputId]="'ad-h-' + advert.advertNumber" [(ngModel)]="advert.triggerHoursBefore" [min]="1" [max]="168" [showButtons]="true" suffix=" h" [disabled]="!ctx.mayEdit()" data-testid="automation.adverts.trigger" />
          </div>
        </div>
        <fieldset class="field channels">
          <legend>{{ 'automation.adverts.channels' | t }}</legend>
          <div class="row">
            @for (channel of channels; track channel) {
              <span class="row">
                <p-checkbox
                  [inputId]="'ad-ch-' + advert.advertNumber + channel"
                  [binary]="true"
                  [ngModel]="advert.channels.includes(channel)"
                  (ngModelChange)="advert.channels = toggle(advert.channels, channel, $event)"
                  [disabled]="!ctx.mayEdit()"
                  [attr.data-testid]="'automation.adverts.channel.' + channel"
                />
                <label [for]="'ad-ch-' + advert.advertNumber + channel">{{ 'automation.channel.' + channel | t }}</label>
              </span>
            }
          </div>
        </fieldset>
        @if (hasTelegram(advert.channels)) {
          <div class="field">
            <label [for]="'ad-tg-' + advert.advertNumber">{{ 'automation.adverts.telegramChat' | t }}</label>
            <p-select
              [inputId]="'ad-tg-' + advert.advertNumber"
              [options]="chatOptions()"
              optionLabel="title"
              optionValue="chatId"
              [(ngModel)]="advert.telegramChatId"
              [showClear]="true"
              [placeholder]="'automation.adverts.telegramDefault' | t"
              [disabled]="!ctx.mayEdit()"
              data-testid="automation.adverts.telegramChat"
            />
          </div>
        }
        @for (channel of advert.channels; track channel) {
          <div class="field messages" [attr.data-testid]="'automation.adverts.messages.' + channel">
            <strong>{{ 'automation.adverts.messagesFor' | t: { channel: ('automation.channel.' + channel | t) } }}</strong>
            <span class="muted">{{ 'automation.adverts.rotating' | t }}</span>
            @for (message of messages(advert, channel); track $index; let m = $index) {
              <div class="message">
                <fs-template-editor
                  [value]="message"
                  (valueChange)="setMessage(advert, channel, m, $event)"
                  [placeholders]="placeholders"
                  [values]="previewValues()"
                  [maxLength]="1000"
                  [inputId]="'ad-msg-' + advert.advertNumber + channel + m"
                  [testId]="'automation.adverts.message.' + channel + '.' + m"
                />
                @if (ctx.mayEdit()) {
                  <p-button icon="pi pi-trash" severity="danger" [text]="true" size="small" [ariaLabel]="'automation.common.remove' | t" (onClick)="removeMessage(advert, channel, m)" />
                }
              </div>
            }
            @if (ctx.mayEdit() && messages(advert, channel).length < maxMessages) {
              <p-button [label]="'automation.adverts.addMessage' | t" icon="pi pi-plus" [text]="true" size="small" (onClick)="addMessage(advert, channel)" [attr.data-testid]="'automation.adverts.addMessage.' + channel" />
            }
          </div>
        }
        <details class="late" [open]="advert.sendLateCancellationMessage">
          <summary>{{ 'automation.adverts.lateTitle' | t }}</summary>
          <div class="switch-row">
            <label [for]="'ad-late-' + advert.advertNumber">{{ 'automation.adverts.lateEnabled' | t }}</label>
            <p-toggleswitch [inputId]="'ad-late-' + advert.advertNumber" [(ngModel)]="advert.sendLateCancellationMessage" [disabled]="!ctx.mayEdit()" data-testid="automation.adverts.lateEnabled" />
          </div>
          <div class="field">
            <label [for]="'ad-lateh-' + advert.advertNumber">{{ 'automation.adverts.lateHours' | t }}</label>
            <p-inputnumber [inputId]="'ad-lateh-' + advert.advertNumber" [(ngModel)]="advert.lateCancellationMaxHoursBefore" [min]="1" [max]="168" [showButtons]="true" suffix=" h" [disabled]="!ctx.mayEdit()" />
          </div>
          <fs-template-editor
            [(value)]="advert.lateCancellationMessage!"
            [placeholders]="placeholders"
            [values]="previewValues()"
            [label]="'automation.adverts.lateMessage' | t"
            [inputId]="'ad-latemsg-' + advert.advertNumber"
            testId="automation.adverts.lateMessage"
          />
          <div class="row">
            <p-checkbox [inputId]="'ad-resend-' + advert.advertNumber" [binary]="true" [(ngModel)]="advert.resendLatestAdvertAfterLateCancellation" [disabled]="!ctx.mayEdit()" />
            <label [for]="'ad-resend-' + advert.advertNumber">{{ 'automation.adverts.resend' | t }}</label>
          </div>
        </details>
        @if (ctx.mayEdit()) {
          <div class="actions">
            <p-button [label]="'common.save' | t" icon="pi pi-save" [loading]="save.busy()" [disabled]="!dirty(advert)" (onClick)="save.run(advert)" data-testid="automation.adverts.save" />
            <p-button [label]="'automation.adverts.delete' | t" icon="pi pi-trash" severity="danger" [text]="true" [loading]="remove.busy()" (onClick)="askRemove(advert)" data-testid="automation.adverts.delete" />
          </div>
        }
      </section>
    }
    @if (save.error(); as e) {
      <p class="error-text" role="alert" data-testid="automation.adverts.error">{{ 'errors.' + e.code | t }}</p>
    }
    @if (ctx.mayEdit() && drafts().length < maxAdverts) {
      <div class="actions">
        <p-button [label]="'automation.adverts.add' | t" icon="pi pi-plus" [outlined]="true" (onClick)="add()" data-testid="automation.adverts.add" />
      </div>
    }

    <section class="card fs-card" data-testid="automation.adverts.planned">
      <div class="row">
        <h3 class="grow">{{ 'automation.adverts.plannedTitle' | t }}</h3>
        @if (ctx.mayEdit() && ctx.data()?.adverts?.length) {
          <p-button [label]="'automation.common.runDry' | t" icon="pi pi-eye" [outlined]="true" size="small" [disabled]="unsaved()" [loading]="run.busy()" (onClick)="run.run(true)" data-testid="automation.adverts.runDry" />
          <p-button [label]="'automation.common.runNow' | t" icon="pi pi-play" severity="warn" [outlined]="true" size="small" [disabled]="unsaved()" [loading]="run.busy()" (onClick)="run.run(false)" data-testid="automation.adverts.run" />
        }
      </div>
      @if (ctx.mayEdit() && ctx.data()?.adverts?.length && unsaved()) {
        <p class="muted" data-testid="automation.adverts.saveFirst">{{ 'automation.adverts.saveFirst' | t }}</p>
      }
      @if (lastRun(); as r) {
        <p class="banner banner--info" data-testid="automation.adverts.runResult">{{ 'automation.adverts.runResult' | t: { evaluated: r.evaluated, sent: r.confirmed, skipped: r.skipped, failed: r.failed } }}</p>
      }
      <fs-data-state [state]="planned" skeleton="table" [emptyTitle]="'automation.adverts.plannedEmpty' | t" testId="automation.adverts.plannedState" data-testid="automation.adverts.plannedState" [compact]="true">
        <ng-template [fsData]="planned" let-rows>
          <div class="table-wrap" tabindex="0">
            <table>
              <thead>
                <tr>
                  <th>{{ 'automation.adverts.pickup' | t }}</th>
                  <th>{{ 'automation.adverts.triggerAt' | t }}</th>
                  <th>#</th>
                  <th>{{ 'automation.adverts.channels' | t }}</th>
                  <th>{{ 'automation.adverts.free' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track row.pickupAt + row.advertNumber) {
                  <tr data-testid="automation.adverts.plannedRow">
                    <td>{{ row.pickupAt | fsDate: 'dateTime' : ctx.timeZone() }}</td>
                    <td>{{ row.triggerAt | fsDate: 'dateTime' : ctx.timeZone() }}</td>
                    <td>{{ row.advertNumber }}</td>
                    <td>{{ channelList(row) }}</td>
                    <td>{{ row.freeSlots }}</td>
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
  styles: `
    .channels { border: 0; margin: 0; padding: 0; }
    .messages { padding: 0.6rem; border: 1px dashed var(--fs-border); border-radius: var(--fs-radius-md); }
    .message { display: grid; grid-template-columns: 1fr auto; gap: 0.35rem; align-items: start; }
    .late { display: grid; gap: 0.6rem; }
    .late summary { cursor: pointer; font-weight: 600; margin-bottom: 0.5rem; }
    p-select { width: 100%; }
  `,
})
export class AdvertsSection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly telegramApi = inject(TelegramService);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);

  protected readonly channels = CHANNELS;
  protected readonly placeholders = ADVERT_PLACEHOLDERS;
  protected readonly maxAdverts = MAX_ADVERTS;
  protected readonly maxMessages = MAX_MESSAGES;
  protected readonly toggle = toggle;
  protected hasTelegram(channels: readonly string[]): boolean {
    return channels.includes('TELEGRAM');
  }
  protected readonly drafts = signal<OpenSlotAdvert[]>([]);
  protected readonly lastRun = signal<AutomationRunResult | null>(null);

  protected readonly planned = loadResource<PlannedAdvert[], string>({
    params: () => this.ctx.storeId(),
    loader: (storeId) => this.api.listPlannedAdverts({ storeId, days: 7 }),
  });

  protected readonly chats = loadResource<TelegramChatConfigList, string>({
    params: () => (this.ctx.mayEdit() ? this.ctx.storeId() : undefined),
    loader: (storeId) => this.telegramApi.getStoreTelegramChats({ storeId }),
    isEmpty: () => false,
  });
  protected readonly chatOptions = computed(() => this.chats.data()?.chats ?? []);

  /** Placeholder values of the next planned pickup (or now). */
  protected readonly previewValues = computed(() => {
    const next = this.planned.data()?.[0];
    const store = this.ctx.store.data();
    return advertValues(
      next?.pickupAt ?? new Date().toISOString(),
      next?.freeSlots ?? 1,
      store?.name ?? '',
      typeof location === 'undefined' ? '' : `${location.origin}/stores/${store?.id ?? ''}/pickups`,
      this.i18n.locale(),
      this.ctx.timeZone(),
    );
  });

  constructor() {
    effect(() => {
      const adverts = this.ctx.data()?.adverts ?? [];
      untracked(() => this.drafts.set(adverts.map((a) => clone(a))));
    });
  }

  protected saved(number: number): OpenSlotAdvert | undefined {
    return this.ctx.data()?.adverts.find((a) => a.advertNumber === number);
  }

  protected dirty(advert: OpenSlotAdvert): boolean {
    const saved = this.saved(advert.advertNumber!);
    return !saved || JSON.stringify(saved) !== JSON.stringify(advert);
  }

  /**
   * "Run now" works on the saved adverts: while a draft differs from the server state, or a save/delete is still in
   * flight (and until the saved state is reloaded), a run would evaluate the old configuration.
   */
  protected unsaved(): boolean {
    return this.save.busy() || this.remove.busy() || this.drafts().some((advert) => this.dirty(advert));
  }

  protected messages(advert: OpenSlotAdvert, channel: AdvertChannel): string[] {
    return advert[LIST_OF[channel]];
  }

  protected setMessage(advert: OpenSlotAdvert, channel: AdvertChannel, index: number, value: string): void {
    advert[LIST_OF[channel]][index] = value;
  }

  protected addMessage(advert: OpenSlotAdvert, channel: AdvertChannel): void {
    advert[LIST_OF[channel]].push('');
  }

  protected removeMessage(advert: OpenSlotAdvert, channel: AdvertChannel, index: number): void {
    advert[LIST_OF[channel]].splice(index, 1);
  }

  protected channelList(row: PlannedAdvert): string {
    return row.channels.map((c) => this.i18n.t(`automation.channel.${c}`)).join(', ');
  }

  protected add(): void {
    const used = new Set(this.drafts().map((a) => a.advertNumber));
    const number = [1, 2, 3, 4, 5].find((n) => !used.has(n))!;
    const advert = emptyAdvert(number);
    advert.pushMessages = [this.i18n.t('automation.adverts.defaultMessage')];
    this.drafts.update((list) => [...list, advert].sort((a, b) => a.advertNumber! - b.advertNumber!));
  }

  private payload(advert: OpenSlotAdvert): OpenSlotAdvert {
    const clean = (list: string[]) => list.map((m) => m.trim()).filter(Boolean);
    return {
      ...advert,
      storeMessages: clean(advert.storeMessages),
      pushMessages: clean(advert.pushMessages),
      telegramMessages: clean(advert.telegramMessages),
      telegramChatId: advert.channels.includes(AdvertChannel.Telegram) ? advert.telegramChatId || undefined : undefined,
      lateCancellationMessage: advert.lateCancellationMessage?.trim() || undefined,
    };
  }

  protected readonly save = runMutation(
    (advert: OpenSlotAdvert) =>
      this.api.putOpenSlotAdvert({ storeId: this.ctx.storeId()!, advertNumber: advert.advertNumber!, openSlotAdvert: this.payload(advert) }),
    {
      successToastKey: 'automation.common.saved',
      errorToast: false,
      onSuccess: () => {
        this.ctx.reload();
        this.planned.reload();
      },
    },
  );

  protected readonly remove = runMutation(
    (advert: OpenSlotAdvert) => this.api.deleteOpenSlotAdvert({ storeId: this.ctx.storeId()!, advertNumber: advert.advertNumber! }),
    {
      successToastKey: 'automation.adverts.deleted',
      onSuccess: () => {
        this.ctx.reload();
        this.planned.reload();
      },
    },
  );

  protected askRemove(advert: OpenSlotAdvert): void {
    if (!this.saved(advert.advertNumber!)) {
      this.drafts.update((list) => list.filter((a) => a !== advert));
      return;
    }
    this.confirm.confirm({
      header: this.i18n.t('automation.adverts.delete'),
      message: this.i18n.t('automation.adverts.deleteText', { number: advert.advertNumber! }),
      acceptLabel: this.i18n.t('automation.adverts.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(advert),
    });
  }

  protected readonly run = runMutation((dryRun: boolean) => this.api.runAdverts({ storeId: this.ctx.storeId()!, dryRun }), {
    successToastKey: 'automation.common.ran',
    onSuccess: (result) => {
      this.lastRun.set(result);
      this.planned.reload();
    },
  });
}
