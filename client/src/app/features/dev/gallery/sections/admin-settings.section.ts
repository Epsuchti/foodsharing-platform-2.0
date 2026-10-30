import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { UploadAdminSummary } from '../../../../api';
import { SettingsTabsComponent } from '../../../admin-settings/components/settings-tabs.component';
import { UploadSummaryComponent } from '../../../admin-settings/components/upload-summary.component';

const SUMMARY: UploadAdminSummary = {
  count: 1284,
  bytes: 734_003_200,
  unclaimedCount: 7,
  byModule: [
    { module: 'USER_PHOTO', count: 912, bytes: 402_653_184 },
    { module: 'FSP_PHOTO', count: 61, bytes: 48_234_496 },
    { module: 'BASKET_PHOTO', count: 230, bytes: 210_763_776 },
    { module: 'mailbox', count: 74, bytes: 71_303_168 },
    { module: 'UNCLAIMED', count: 7, bytes: 1_048_576 },
  ],
};

/** Admin settings (plan-m7 Q13, C10–C14): the settings tab bar and the uploads storage summary. */
@Component({
  selector: 'fs-gallery-admin-settings-section',
  imports: [SettingsTabsComponent, UploadSummaryComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-admin-settings">Admin settings</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.adminSettings.tabs">
        <header class="specimen__head"><code>fs-settings-tabs</code></header>
        <fs-settings-tabs />
      </section>
      <section class="specimen" data-testid="gallery.adminSettings.uploadSummary">
        <header class="specimen__head"><code>fs-upload-summary</code></header>
        <fs-upload-summary [summary]="summary" />
      </section>
    </div>
  `,
})
export class AdminSettingsGallerySection {
  protected readonly summary = SUMMARY;
}

export const ADMIN_SETTINGS_GALLERY_SECTIONS: readonly Type<unknown>[] = [AdminSettingsGallerySection];
