import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { MultiSelect } from '@openng/optimus-ui/multiselect';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  AutomationService,
  Cancellation,
  SignoutAlertChannel,
  SignoutAlertSetting,
  TelegramChatConfigList,
  TelegramService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { clone, decisionTone, emptySignoutAlert, toggle } from '../automation.util';
import { StoreAutomationContext } from '../store-automation.context';

const CHANNELS: SignoutAlertChannel[] = [SignoutAlertChannel.Bell, SignoutAlertChannel.Push, SignoutAlertChannel.Telegram];

/** Sign-out alerts (AU5): settings (cutoff, channels, Telegram chats) and the cancellations list with status. */
@Component({
  selector: 'fs-signout-alert-section',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, DateTimePipe, FormsModule, InputNumber, MultiSelect, ToggleSwitch, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (draft(); as s) {
      <section class="card fs-card" data-testid="automation.signout.root" [attr.data-enabled]="s.enabled">
        <h2>{{ 'automation.signout.title' | t }}</h2>
        <p class="muted">{{ 'automation.signout.intro' | t }}</p>
        <div class="switch-row">
          <label for="so-enabled"><strong>{{ 'automation.signout.enabled' | t }}</strong></label>
          <p-toggleswitch inputId="so-enabled" [(ngModel)]="s.enabled" [disabled]="!ctx.mayEdit()" data-testid="automation.signout.enabled" />
        </div>
        <div class="switch-row">
          <label class="switch-row__text" for="so-cutoff"><strong>{{ 'automation.signout.cutoff' | t }}</strong>
            <span class="muted">{{ 'automation.signout.cutoffHint' | t }}</span></label>
          <p-toggleswitch inputId="so-cutoff" [(ngModel)]="s.cutoffEnabled" [disabled]="!ctx.mayEdit()" data-testid="automation.signout.cutoff" />
        </div>
        <div class="field">
          <label for="so-hours">{{ 'automation.signout.hours' | t }}</label>
          <p-inputnumber inputId="so-hours" [(ngModel)]="s.maximumHoursBeforePickup" [min]="1" [max]="336" [showButtons]="true" suffix=" h" [disabled]="!ctx.mayEdit() || !s.cutoffEnabled" data-testid="automation.signout.hours" />
        </div>
        <fieldset class="field plain">
          <legend>{{ 'automation.adverts.channels' | t }}</legend>
          <div class="row">
            @for (channel of channels; track channel) {
              <span class="row">
                <p-checkbox [inputId]="'so-ch-' + channel" [binary]="true" [ngModel]="s.channels.includes(channel)" (ngModelChange)="s.channels = toggle(s.channels, channel, $event)" [disabled]="!ctx.mayEdit()" [attr.data-testid]="'automation.signout.channel.' + channel" />
                <label [for]="'so-ch-' + channel">{{ 'automation.alertChannel.' + channel | t }}</label>
              </span>
            }
          </div>
        </fieldset>
        @if (hasTelegram(s.channels)) {
          <div class="field">
            <label for="so-chats">{{ 'automation.signout.chats' | t }}</label>
            <p-multiselect inputId="so-chats" [options]="chatOptions()" optionLabel="title" optionValue="chatId" [(ngModel)]="s.telegramChatIds" display="chip" [disabled]="!ctx.mayEdit()" data-testid="automation.signout.chats" />
          </div>
        }
        @if (s.activeSince) {
          <p class="muted">{{ 'automation.signout.activeSince' | t: { time: (s.activeSince | fsDate: 'dateTime') } }}</p>
        }
        @if (save.error(); as e) {
          <p class="error-text" role="alert" data-testid="automation.signout.error">{{ 'errors.' + e.code | t }}</p>
        }
        @if (ctx.mayEdit()) {
          <div class="actions">
            <p-button [label]="'common.save' | t" icon="pi pi-save" [loading]="save.busy()" [disabled]="!dirty()" (onClick)="save.run()" data-testid="automation.signout.save" />
          </div>
        }
      </section>
    }

    <section class="card fs-card" data-testid="automation.signout.cancellations">
      <h3>{{ 'automation.signout.cancellationsTitle' | t }}</h3>
      <fs-data-state [state]="cancellations" skeleton="table" [emptyTitle]="'automation.signout.empty' | t" testId="automation.signout.state" data-testid="automation.signout.state" [compact]="true">
        <ng-template [fsData]="cancellations" let-rows>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{{ 'automation.adverts.pickup' | t }}</th>
                  <th>{{ 'automation.signout.user' | t }}</th>
                  <th>{{ 'automation.signout.cancelledAt' | t }}</th>
                  <th>{{ 'automation.signout.status' | t }}</th>
                  <th>{{ 'automation.signout.slots' | t }}</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows; track row.id) {
                  <tr data-testid="automation.signout.row" [attr.data-status]="row.status">
                    <td>{{ row.pickupAt | fsDate: 'dateTime' : ctx.timeZone() }}</td>
                    <td><fs-user-chip [name]="row.user.displayName" [avatarUrl]="row.user.avatarUrl" [userId]="row.user.id" /></td>
                    <td>{{ row.cancelledAt | fsDate: 'dateTime' : ctx.timeZone() }}</td>
                    <td><span [class]="'badge badge--' + tone(row.status)">{{ 'automation.decision.' + row.status | t }}</span></td>
                    <td>
                      @if (row.totalSlots !== undefined && row.totalSlots !== null) {
                        {{ 'automation.signout.free' | t: { free: row.totalSlots - (row.occupiedSlots ?? 0), total: row.totalSlots } }}
                      }
                    </td>
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
    .plain { border: 0; margin: 0; padding: 0; }
    p-multiselect { width: 100%; }
  `,
})
export class SignoutAlertSection {
  protected readonly ctx = inject(StoreAutomationContext);
  private readonly api = inject(AutomationService);
  private readonly telegramApi = inject(TelegramService);

  protected readonly channels = CHANNELS;
  protected readonly toggle = toggle;
  protected hasTelegram(channels: readonly string[]): boolean {
    return channels.includes('TELEGRAM');
  }
  protected readonly tone = decisionTone;
  protected readonly draft = signal<SignoutAlertSetting | null>(null);

  protected readonly cancellations = loadResource<Cancellation[], string>({
    params: () => this.ctx.storeId(),
    loader: (storeId) => this.api.listCancellations({ storeId }),
  });

  private readonly chats = loadResource<TelegramChatConfigList, string>({
    params: () => (this.ctx.mayEdit() ? this.ctx.storeId() : undefined),
    loader: (storeId) => this.telegramApi.getStoreTelegramChats({ storeId }),
    isEmpty: () => false,
  });
  protected readonly chatOptions = computed(() => this.chats.data()?.chats ?? []);

  constructor() {
    effect(() => {
      const setting = this.ctx.data()?.signoutAlert;
      untracked(() => this.draft.set(setting ? clone(setting) : emptySignoutAlert()));
    });
  }

  protected dirty(): boolean {
    const draft = this.draft();
    const saved = this.ctx.data()?.signoutAlert;
    return !!draft && (!saved || JSON.stringify(draft) !== JSON.stringify(saved));
  }

  protected readonly save = runMutation(
    () => {
      const { activeSince: _ignored, ...setting } = this.draft()!;
      return this.api.putSignoutAlert({ storeId: this.ctx.storeId()!, signoutAlertSetting: setting });
    },
    { successToastKey: 'automation.common.saved', errorToast: false, onSuccess: () => this.ctx.reload() },
  );
}
