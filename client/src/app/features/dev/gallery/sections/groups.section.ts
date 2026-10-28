import { ChangeDetectionStrategy, Component, Type, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { GroupFormDialogComponent } from '../../../regions/shared/group-form-dialog.component';

/** GroupFormDialog (create / edit a working group). */
@Component({
  selector: 'fs-gallery-groups-section',
  imports: [Button, GroupFormDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-groups">Working groups</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.groupForm">
        <header class="specimen__head"><code>fs-group-form-dialog</code></header>
        <p-button label="Open create dialog" icon="pi pi-plus" (onClick)="open.set(true)" data-testid="gallery.groupForm.open" />
        <fs-group-form-dialog [(visible)]="open" [allowRestricted]="true" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class GroupsGallerySection {
  protected readonly open = signal(false);
}

/** Gallery sections of the groups feature (one file per module, no conflicts). */
export const GROUPS_GALLERY_SECTIONS: readonly Type<unknown>[] = [GroupsGallerySection];
