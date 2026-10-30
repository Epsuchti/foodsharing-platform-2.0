import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { map } from 'rxjs';

import { AdminSettingsService, FeatureToggleKey, FeatureToggleState } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { toggleOrder } from '../admin-settings.util';
import { SettingsTabsComponent } from '../components/settings-tabs.component';

/**
 * `/admin/feature-toggles` (C11, ORGA): every feature toggle with its state and default. Switching a feature off
 * asks first (its pages, nav entries and API answer 404 FEATURE_DISABLED for everyone); switching on is immediate.
 * The change is applied to this tab's FeatureToggleService right away.
 */
@Component({
  selector: 'fs-feature-toggles-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, SettingsTabsComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.toggles.title' | t" [subtitle]="'adminSettings.toggles.subtitle' | t" icon="pi pi-power-off" />
    <fs-settings-tabs />
    <fs-data-state
      [state]="toggles"
      skeleton="list"
      [skeletonRows]="6"
      [emptyTitle]="'adminSettings.toggles.empty' | t"
      testId="adminSettings.toggles.state"
      data-testid="adminSettings.toggles.state"
    >
      <ng-template [fsData]="toggles" let-list>
        <ul class="list fs-card" data-testid="adminSettings.toggles.list">
          @for (toggle of list; track toggle.key) {
            <li class="row" data-testid="adminSettings.toggles.row" [attr.data-key]="toggle.key" [attr.data-enabled]="toggle.enabled">
              <div>
                <h2 class="row__title">
                  {{ 'adminSettings.toggles.key.' + toggle.key | t }}
                  <span [class]="toggle.enabled ? 'chip chip--on' : 'chip chip--off'" data-testid="adminSettings.toggles.status">
                    {{ (toggle.enabled ? 'adminSettings.toggles.on' : 'adminSettings.toggles.off') | t }}
                  </span>
                </h2>
                <p class="row__text">{{ toggle.description || ('adminSettings.toggles.text.' + toggle.key | t) }}</p>
                <p class="row__text muted">
                  {{ (toggle.defaultEnabled ? 'adminSettings.toggles.defaultOn' : 'adminSettings.toggles.defaultOff') | t }}
                  @if (toggle.updatedAt) {
                    · {{ 'adminSettings.toggles.changedAt' | t: { date: (toggle.updatedAt | fsDate: 'dateTime') } }}
                  }
                </p>
              </div>
              <div class="row__actions">
                <p-button
                  [label]="(toggle.enabled ? 'adminSettings.toggles.disable' : 'adminSettings.toggles.enable') | t"
                  [icon]="toggle.enabled ? 'pi pi-power-off' : 'pi pi-check'"
                  [severity]="toggle.enabled ? 'danger' : 'primary'"
                  [outlined]="toggle.enabled"
                  size="small"
                  [loading]="busyKey() === toggle.key"
                  [disabled]="update.busy()"
                  [ariaLabel]="(toggle.enabled ? 'adminSettings.toggles.disableFor' : 'adminSettings.toggles.enableFor') | t: { name: ('adminSettings.toggles.key.' + toggle.key | t) }"
                  (onClick)="flip(toggle)"
                  data-testid="adminSettings.toggles.switch"
                />
              </div>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-settings.scss',
})
export class FeatureTogglesPageComponent {
  private readonly api = inject(AdminSettingsService);
  private readonly featureToggles = inject(FeatureToggleService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly busyKey = signal<string | null>(null);

  protected readonly toggles = loadResource<FeatureToggleState[]>({
    loader: () =>
      this.api.listFeatureToggles().pipe(map((list) => [...list].sort((a, b) => toggleOrder(a.key) - toggleOrder(b.key)))),
  });

  protected readonly update = runMutation(
    (key: FeatureToggleKey, enabled: boolean) => {
      this.busyKey.set(key);
      return this.api.updateFeatureToggle({ key, featureToggleUpdate: { enabled } });
    },
    {
      successToastKey: 'adminSettings.toggles.saved',
      onSuccess: (state) => {
        this.busyKey.set(null);
        this.featureToggles.apply(state.key, state.enabled);
        this.toggles.update((list) => (list ?? []).map((item) => (item.key === state.key ? state : item)));
      },
      onError: () => {
        this.busyKey.set(null);
        this.toggles.reload();
      },
    },
  );


  protected flip(toggle: FeatureToggleState): void {
    if (!toggle.enabled) {
      void this.update.run(toggle.key, true);
      return;
    }
    const name = this.i18n.t(`adminSettings.toggles.key.${toggle.key}`);
    this.confirm.confirm({
      header: this.i18n.t('adminSettings.toggles.disableTitle', { name }),
      message: this.i18n.t('adminSettings.toggles.disableText'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('adminSettings.toggles.disable'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.update.run(toggle.key, false),
    });
  }

}
