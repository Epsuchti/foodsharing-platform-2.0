import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Drawer } from '@openng/optimus-ui/drawer';
import { InputText } from '@openng/optimus-ui/inputtext';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { AdminSettingsService, ConfigurationEntry, HistorySubjectType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { errorParams } from '../../../core/data/api-error';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { AdminSettingsResources } from '../admin-settings.resource';
import { ConfigurationTypeName, configurationGroup, configurationLabelKey, configurationType } from '../admin-settings.util';
import { SettingsTabsComponent } from '../components/settings-tabs.component';

interface ConfigGroup {
  id: string;
  entries: ConfigurationEntry[];
}

/** Groups with a heading text; other key prefixes show the prefix itself. */
const KNOWN_GROUPS = ['support', 'petition', 'donation', 'statistics', 'oauth'];

/**
 * `/admin/configuration` (C14, ORGA): every configuration value grouped by its key prefix, with an input that
 * fits its type (switch, number, email, URL, comma-separated list, text). Each row saves on its own; an invalid
 * value (400 CONFIGURATION_VALUE_INVALID) is shown below the row. The history drawer lists the
 * ADMIN_CONFIGURATION_CHANGED entries of the SYSTEM subject with old and new value.
 */
@Component({
  selector: 'fs-configuration-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    Drawer,
    FormsModule,
    HistoryTimelineComponent,
    InputText,
    PageHeaderComponent,
    SettingsTabsComponent,
    TPipe,
    ToggleSwitch,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.configuration.title' | t" [subtitle]="'adminSettings.configuration.subtitle' | t" icon="pi pi-cog">
      <p-button
        [label]="'adminSettings.configuration.history' | t"
        icon="pi pi-history"
        severity="secondary"
        [outlined]="true"
        (onClick)="historyOpen.set(true)"
        data-testid="adminSettings.configuration.historyButton"
      />
    </fs-page-header>
    <fs-settings-tabs />
    <fs-data-state
      [state]="entries"
      skeleton="form"
      [skeletonRows]="6"
      [emptyTitle]="'adminSettings.configuration.empty' | t"
      testId="adminSettings.configuration.state"
      data-testid="adminSettings.configuration.state"
    >
      <ng-template [fsData]="entries">
        @for (group of groups(); track group.id) {
          <h2 class="section-title">{{ groupLabel(group.id) }}</h2>
          <ul class="list fs-card">
            @for (entry of group.entries; track entry.key) {
              @let type = typeOf(entry);
              @let inputId = 'config-' + entry.key;
              <li class="row" data-testid="adminSettings.configuration.row" [attr.data-key]="entry.key" [attr.data-type]="type">
                <div class="field">
                  <label [for]="inputId">{{ labelOf(entry.key) }}</label>
                  <code class="muted">{{ entry.key }}</code>
                  @if (type === 'BOOLEAN') {
                    <p-toggleswitch
                      [inputId]="inputId"
                      [ngModel]="draftOf(entry) === 'true'"
                      (ngModelChange)="setDraft(entry.key, $event ? 'true' : 'false')"
                      data-testid="adminSettings.configuration.input"
                    />
                  } @else {
                    <input
                      pInputText
                      [id]="inputId"
                      [type]="inputType(type)"
                      [attr.inputmode]="type === 'INTEGER' ? 'numeric' : null"
                      [ngModel]="draftOf(entry)"
                      (ngModelChange)="setDraft(entry.key, $event)"
                      (keydown.enter)="saveRow(entry)"
                      [attr.aria-invalid]="!!errorOf(entry.key)"
                      [attr.aria-describedby]="inputId + '-hint'"
                      data-testid="adminSettings.configuration.input"
                    />
                  }
                  <span class="muted" [id]="inputId + '-hint'">
                    {{ 'adminSettings.configuration.type.' + type | t }}
                    · {{ 'adminSettings.configuration.default' | t: { value: entry.defaultValue || '–' } }}
                    @if (entry.updatedAt) {
                      @if (entry.updatedBy) {
                        · {{ 'adminSettings.configuration.changedAtBy' | t: { date: (entry.updatedAt | fsDate: 'dateTime'), name: entry.updatedBy.displayName } }}
                      } @else {
                        · {{ 'adminSettings.configuration.changedAt' | t: { date: (entry.updatedAt | fsDate: 'dateTime') } }}
                      }
                    }
                  </span>
                  @if (errorOf(entry.key); as error) {
                    <p class="field-error" role="alert" data-testid="adminSettings.configuration.error">{{ error }}</p>
                  }
                </div>
                <div class="row__actions">
                  @if (draftOf(entry) !== entry.defaultValue) {
                    <p-button
                      [label]="'adminSettings.configuration.useDefault' | t"
                      icon="pi pi-undo"
                      size="small"
                      severity="secondary"
                      [text]="true"
                      (onClick)="setDraft(entry.key, entry.defaultValue)"
                      data-testid="adminSettings.configuration.useDefault"
                    />
                  }
                  <p-button
                    [label]="'common.save' | t"
                    icon="pi pi-check"
                    size="small"
                    [disabled]="!isDirty(entry) || save.busy()"
                    [loading]="savingKey() === entry.key"
                    (onClick)="saveRow(entry)"
                    data-testid="adminSettings.configuration.save"
                  />
                </div>
              </li>
            }
          </ul>
        }
      </ng-template>
    </fs-data-state>

    <!-- rendered only while open: a closed drawer would leave its focus trap in the page (axe aria-hidden-focus) -->
    @if (historyOpen()) {
      <p-drawer
        [visible]="true"
        (visibleChange)="!$event && historyOpen.set(false)"
        position="right"
        [header]="'adminSettings.configuration.historyTitle' | t"
        [ariaCloseLabel]="'adminSettings.configuration.closeHistory' | t"
        [style]="{ width: 'min(36rem, 100vw)' }"
        data-testid="adminSettings.configuration.historyDrawer"
      >
        <fs-history-timeline
          [source]="history"
          [subjectType]="systemSubject"
          [filters]="{ dateRange: true, actions: false }"
          [viewerId]="viewerId()"
          [compact]="true"
          [showExportButton]="false"
          [emptyTitle]="'adminSettings.configuration.noHistory' | t"
          testId="adminSettings.configuration.history"
        />
      </p-drawer>
    }
  `,
  styleUrl: '../admin-settings.scss',
})
export class ConfigurationPageComponent {
  private readonly api = inject(AdminSettingsService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  protected readonly systemSubject = HistorySubjectType.System;
  protected readonly historyOpen = signal(false);
  protected readonly viewerId = computed(() => this.session.user()?.id ?? null);
  protected readonly drafts = signal<Record<string, string>>({});
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly savingKey = signal<string | null>(null);

  protected readonly entries = loadResource<ConfigurationEntry[]>({
    loader: () => this.api.listConfiguration(),
  });

  protected readonly groups = computed<ConfigGroup[]>(() => {
    const groups = new Map<string, ConfigurationEntry[]>();
    for (const entry of this.entries.data() ?? []) {
      const id = configurationGroup(entry.key);
      groups.set(id, [...(groups.get(id) ?? []), entry]);
    }
    return [...groups.entries()].map(([id, entries]) => ({ id, entries }));
  });

  protected readonly history = inject(AdminSettingsResources).configurationHistory;

  protected readonly save = runMutation(
    (key: string, value: string) => {
      this.savingKey.set(key);
      return this.api.updateConfiguration({ key, configurationUpdate: { value } });
    },
    {
      // the error is shown below the row instead
      errorToast: false,
      successToastKey: 'adminSettings.configuration.saved',
      onSuccess: (saved) => {
        this.savingKey.set(null);
        this.drafts.update(({ [saved.key]: _, ...rest }) => rest);
        this.errors.update(({ [saved.key]: _, ...rest }) => rest);
        this.entries.update((list) => (list ?? []).map((item) => (item.key === saved.key ? saved : item)));
      },
      onError: (error) => {
        const key = this.savingKey();
        this.savingKey.set(null);
        if (key) {
          this.errors.update((all) => ({ ...all, [key]: this.i18n.error(error.code, errorParams(error)) }));
        }
      },
    },
  );

  protected typeOf(entry: ConfigurationEntry): ConfigurationTypeName {
    return configurationType(entry);
  }

  protected inputType(type: ConfigurationTypeName): string {
    return type === 'INTEGER' ? 'number' : type === 'EMAIL' ? 'email' : type === 'URL' ? 'url' : 'text';
  }

  protected labelOf(key: string): string {
    const labelKey = configurationLabelKey(key);
    return this.i18n.has(labelKey) ? this.i18n.t(labelKey) : key;
  }

  protected groupLabel(id: string): string {
    return KNOWN_GROUPS.includes(id) ? this.i18n.t(`adminSettings.configuration.group.${id}`) : id;
  }

  protected draftOf(entry: ConfigurationEntry): string {
    return this.drafts()[entry.key] ?? entry.value;
  }

  protected errorOf(key: string): string | undefined {
    return this.errors()[key];
  }

  protected isDirty(entry: ConfigurationEntry): boolean {
    return this.draftOf(entry) !== entry.value;
  }

  protected setDraft(key: string, value: unknown): void {
    this.drafts.update((all) => ({ ...all, [key]: value === null || value === undefined ? '' : String(value) }));
    this.errors.update(({ [key]: _, ...rest }) => rest);
  }

  protected saveRow(entry: ConfigurationEntry): void {
    if (this.isDirty(entry)) {
      void this.save.run(entry.key, this.draftOf(entry));
    }
  }
}
