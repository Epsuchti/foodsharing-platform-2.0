import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { forkJoin } from 'rxjs';

import { RegionOptionPermissions, RegionOptions, RegionOptionsPatch, RegionsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RegionPageContext } from '../region-page.context';

interface OptionsData {
  options: RegionOptions;
  permissions: RegionOptionPermissions;
}

/**
 * Options tab (R5): report/mediation buttons (report function group), pickup rule (store coordination group, else
 * ambassadors) and member-related options (ambassadors), each section editable only by who may; the others are
 * read-only with a hint.
 */
@Component({
  selector: 'fs-region-options-tab',
  imports: [...DATA_STATE_IMPORTS, Button, InputNumber, ReactiveFormsModule, TPipe, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './region-options.tab.html',
  styleUrl: './tab.scss',
})
export class RegionOptionsTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(RegionsService);

  protected readonly data = loadResource<OptionsData, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) =>
      forkJoin({
        options: this.api.getRegionOptions({ regionId }),
        permissions: this.api.getRegionOptionPermissions({ regionId }),
      }),
    isEmpty: () => false,
  });

  protected readonly report = new FormGroup({
    enableReportButton: new FormControl(false, { nonNullable: true }),
    enableMediationButton: new FormControl(false, { nonNullable: true }),
    reportReasonOptions: new FormControl(1, { nonNullable: true }),
    reportReasonOther: new FormControl(false, { nonNullable: true }),
  });

  protected readonly pickupRule = new FormGroup({
    active: new FormControl(false, { nonNullable: true }),
    timespanDays: new FormControl(7, { nonNullable: true }),
    limitNumber: new FormControl(2, { nonNullable: true }),
    limitDayNumber: new FormControl(1, { nonNullable: true }),
    inactiveHours: new FormControl(24, { nonNullable: true }),
  });

  protected readonly member = new FormGroup({
    allowHidingInForum: new FormControl(false, { nonNullable: true }),
    notifyAddressChange: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    effect(() => {
      const value = this.data.data();
      if (!value) {
        return;
      }
      const { options, permissions } = value;
      this.report.reset({
        enableReportButton: options.enableReportButton,
        enableMediationButton: options.enableMediationButton,
        reportReasonOptions: options.reportReasonOptions,
        reportReasonOther: options.reportReasonOther,
      });
      this.pickupRule.reset(options.pickupRule);
      this.member.reset({
        allowHidingInForum: options.allowHidingInForum,
        notifyAddressChange: options.notifyAddressChange,
      });
      this.toggle(this.report, permissions.mayEditReportButtons);
      this.toggle(this.pickupRule, permissions.mayEditPickupRule);
      this.toggle(this.member, permissions.mayEditUserRelated);
    });
  }

  private toggle(group: FormGroup, enabled: boolean): void {
    if (enabled) {
      group.enable({ emitEvent: false });
    } else {
      group.disable({ emitEvent: false });
    }
  }

  protected readonly save = runMutation(
    (section: 'report' | 'pickupRule' | 'member') => {
      let patch: RegionOptionsPatch;
      if (section === 'report') {
        patch = this.report.getRawValue();
      } else if (section === 'pickupRule') {
        patch = { pickupRule: this.pickupRule.getRawValue() };
      } else {
        patch = this.member.getRawValue();
      }
      return this.api.updateRegionOptions({ regionId: this.ctx.regionId()!, regionOptionsPatch: patch });
    },
    {
      successToastKey: 'regions.options.saved',
      onSuccess: (options) =>
        this.data.update((current) => (current ? { ...current, options } : current!)),
    },
  );
}
