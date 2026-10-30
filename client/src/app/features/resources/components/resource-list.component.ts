import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { finalize } from 'rxjs';

import { Resource, ResourcesService } from '../../../api';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { ResourceCardComponent } from './resource-card.component';
import { ResourceFormDialogComponent } from './resource-form-dialog.component';

/**
 * Resource cards with their actions (C6): favourite star (`favouriteResource` / `unfavouriteResource`), edit (the
 * form dialog) and delete (confirmed). Emits `changed` after every change so the page reloads its list.
 */
@Component({
  selector: 'fs-resource-list',
  imports: [ResourceCardComponent, ResourceFormDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="list" [attr.data-testid]="testId()">
      @for (resource of resources(); track resource.id) {
        <li>
          <fs-resource-card
            [resource]="resource"
            [busy]="pending() === resource.id"
            (favourite)="toggleFavourite.run(resource)"
            (edit)="startEdit(resource)"
            (remove)="confirmDelete(resource)"
          />
        </li>
      }
    </ul>
    <fs-resource-form-dialog [(visible)]="editing" [resource]="editResource()" (saved)="changed.emit()" />
  `,
  styles: `
    :host { display: block; }
    .list { display: grid; gap: 0.75rem; margin: 0; padding: 0; list-style: none; grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr)); }
  `,
})
export class ResourceListComponent {
  private readonly api = inject(ResourcesService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly resources = input.required<readonly Resource[]>();
  readonly testId = input('resources.list');
  readonly changed = output<void>();

  protected readonly editing = signal(false);
  protected readonly editResource = signal<Resource | null>(null);
  /** Resource whose favourite mark is being saved. */
  protected readonly pending = signal<string | null>(null);

  protected readonly toggleFavourite = runMutation(
    (resource: Resource) => {
      this.pending.set(resource.id);
      const call = resource.favourite
        ? this.api.unfavouriteResource({ resourceId: resource.id })
        : this.api.favouriteResource({ resourceId: resource.id });
      return call.pipe(finalize(() => this.pending.set(null)));
    },
    { onSuccess: () => this.changed.emit() },
  );

  protected readonly remove = runMutation((resource: Resource) => this.api.deleteResource({ resourceId: resource.id }), {
    successToastKey: 'resources.card.deleted',
    onSuccess: () => this.changed.emit(),
  });

  protected startEdit(resource: Resource): void {
    this.editResource.set(resource);
    this.editing.set(true);
  }

  protected confirmDelete(resource: Resource): void {
    this.confirm.confirm({
      header: this.i18n.t('resources.card.deleteTitle', { name: resource.name }),
      message: this.i18n.t('resources.card.deleteText'),
      acceptLabel: this.i18n.t('resources.card.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(resource),
    });
  }
}
