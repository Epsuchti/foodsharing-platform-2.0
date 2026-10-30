import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';

import { AdminUploadsService, UploadAdminItem, UploadAdminPage, UploadAdminSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { formatBytes, isImage } from '../admin-settings.util';
import { UploadSummaryComponent, uploadModuleLabel } from '../components/upload-summary.component';

export const UPLOADS_PAGE_SIZE = 25;
/** Filter values besides the module names of the summary. */
export const ALL_UPLOADS = 'ALL';
export const UNCLAIMED_UPLOADS = 'UNCLAIMED';

/** Query of the list: the module filter wins over `unclaimed` on the server, so only one is sent. */
export function uploadQuery(filter: string, page: number): { unclaimed?: boolean; module?: string; page: number; size: number } {
  if (filter === UNCLAIMED_UPLOADS) {
    return { unclaimed: true, page, size: UPLOADS_PAGE_SIZE };
  }
  if (filter === ALL_UPLOADS) {
    return { page, size: UPLOADS_PAGE_SIZE };
  }
  return { module: filter, page, size: UPLOADS_PAGE_SIZE };
}

/**
 * `/admin/uploads` (C10, ORGA): storage usage (total, unclaimed, per module) and every upload newest first with
 * a preview (`?size=sm` for images), owner, module and expiry; filter by module or unclaimed; delete (also the
 * variants). Unclaimed uploads expire after a day and are removed by the nightly cleanup job.
 */
@Component({
  selector: 'fs-uploads-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, PageHeaderComponent, Select, TPipe, UploadSummaryComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminSettings.uploads.title' | t" [subtitle]="'adminSettings.uploads.subtitle' | t" icon="pi pi-images" />
    <fs-data-state [state]="summary" skeleton="card" [skeletonRows]="1" [compact]="true" testId="adminSettings.uploads.summaryState" data-testid="adminSettings.uploads.summaryState">
      <ng-template [fsData]="summary" let-sum>
        <fs-upload-summary [summary]="sum" />
      </ng-template>
    </fs-data-state>

    <div class="filters">
      <div class="field">
        <label for="uploads-filter">{{ 'adminSettings.uploads.filter' | t }}</label>
        <p-select
          inputId="uploads-filter"
          [options]="filterOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="filter()"
          (ngModelChange)="setFilter($event)"
          appendTo="body"
          [style]="{ width: '100%' }"
          data-testid="adminSettings.uploads.filterSelect"
        >
          <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
        </p-select>
      </div>
    </div>

    <fs-data-state
      [state]="uploads"
      skeleton="table"
      [skeletonRows]="8"
      emptyIcon="pi pi-images"
      [emptyTitle]="'adminSettings.uploads.empty' | t"
      testId="adminSettings.uploads.state"
      data-testid="adminSettings.uploads.state"
    >
      <ng-template [fsData]="uploads" let-result>
        <div class="table-wrap fs-card">
          <table class="table" data-testid="adminSettings.uploads.table">
            <thead>
              <tr>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminSettings.uploads.preview' | t }}</span></th>
                <th scope="col">{{ 'adminSettings.uploads.file' | t }}</th>
                <th scope="col">{{ 'adminSettings.uploads.owner' | t }}</th>
                <th scope="col">{{ 'adminSettings.uploads.usage' | t }}</th>
                <th scope="col">{{ 'adminSettings.uploads.created' | t }}</th>
                <th scope="col" class="num">{{ 'adminSettings.uploads.size' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminSettings.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (upload of result.items; track upload.id) {
                <tr data-testid="adminSettings.uploads.row" [attr.data-id]="upload.id" [attr.data-module]="upload.usageModule ?? unclaimedValue">
                  <td class="preview">
                    @if (image(upload.mimeType)) {
                      <img [src]="'/api/uploads/' + upload.id + '?size=sm'" alt="" width="48" height="48" loading="lazy" />
                    } @else {
                      <i class="pi pi-file" aria-hidden="true"></i>
                    }
                  </td>
                  <td>
                    <span class="name">{{ upload.fileName }}</span>
                    <span class="muted">{{ upload.mimeType }}</span>
                  </td>
                  <td>{{ upload.owner?.displayName ?? ('adminSettings.uploads.system' | t) }}</td>
                  <td>
                    @if (upload.usageModule) {
                      <span class="chip">{{ moduleLabel(upload.usageModule) }}</span>
                    } @else {
                      <span class="chip chip--warn" data-testid="adminSettings.uploads.unclaimedTag">{{ 'adminSettings.uploads.unclaimedOne' | t }}</span>
                      @if (upload.expiresAt) {
                        <span class="muted">{{ 'adminSettings.uploads.expires' | t: { date: (upload.expiresAt | fsDate: 'dateTime') } }}</span>
                      }
                    }
                  </td>
                  <td>{{ upload.createdAt | fsDate: 'dateTime' }}</td>
                  <td class="num">{{ bytes(upload.size) }}</td>
                  <td class="actions">
                    <p-button
                      icon="pi pi-trash"
                      severity="danger"
                      [text]="true"
                      [rounded]="true"
                      [ariaLabel]="'adminSettings.uploads.deleteFor' | t: { name: upload.fileName }"
                      (onClick)="confirmDelete(upload)"
                      data-testid="adminSettings.uploads.delete"
                    />
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.page.totalPages > 1) {
          <div class="pager">
            <p-button icon="pi pi-angle-left" [text]="true" [disabled]="page() === 0" (onClick)="page.set(page() - 1)" [ariaLabel]="'adminSettings.previous' | t" data-testid="adminSettings.uploads.prev" />
            <span>{{ 'adminSettings.pageOf' | t: { page: page() + 1, pages: result.page.totalPages } }}</span>
            <p-button icon="pi pi-angle-right" [text]="true" [disabled]="page() + 1 >= result.page.totalPages" (onClick)="page.set(page() + 1)" [ariaLabel]="'adminSettings.next' | t" data-testid="adminSettings.uploads.next" />
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-settings.scss',
  styles: `
    .preview {
      width: 3.5rem;
    }
    .preview img {
      display: block;
      width: 3rem;
      height: 3rem;
      border-radius: var(--fs-radius-sm);
      object-fit: cover;
      background: var(--fs-surface-muted);
    }
    .preview .pi {
      font-size: 1.5rem;
      color: var(--fs-text-muted);
    }
    .name {
      display: block;
      max-width: 18rem;
      overflow-wrap: anywhere;
    }
    .muted {
      display: block;
    }
  `,
})
export class UploadsPageComponent {
  private readonly api = inject(AdminUploadsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly unclaimedValue = UNCLAIMED_UPLOADS;
  protected readonly filter = signal<string>(ALL_UPLOADS);
  protected readonly page = signal(0);

  protected readonly summary = loadResource<UploadAdminSummary>({
    loader: () => this.api.getUploadSummary(),
    isEmpty: () => false,
  });

  protected readonly uploads = loadResource<UploadAdminPage, { filter: string; page: number }>({
    params: () => ({ filter: this.filter(), page: this.page() }),
    loader: ({ filter, page }) => this.api.listAdminUploads(uploadQuery(filter, page)),
    isEmpty: (result) => result.items.length === 0,
  });

  protected readonly filterOptions = computed(() => {
    this.i18n.language();
    const modules = (this.summary.data()?.byModule ?? []).map((m) => m.module).filter((m) => m !== UNCLAIMED_UPLOADS);
    return [
      { value: ALL_UPLOADS, label: this.i18n.t('adminSettings.uploads.all') },
      { value: UNCLAIMED_UPLOADS, label: this.i18n.t('adminSettings.uploads.unclaimed') },
      ...modules.map((module) => ({ value: module, label: this.moduleLabel(module) })),
    ];
  });

  protected readonly remove = runMutation((upload: UploadAdminItem) => this.api.deleteAdminUpload({ uploadId: upload.id }), {
    successToastKey: 'adminSettings.uploads.deleted',
    onSuccess: () => {
      this.uploads.reload();
      this.summary.reload();
    },
  });

  protected bytes(value: number): string {
    return formatBytes(value);
  }

  protected image(mimeType: string): boolean {
    return isImage(mimeType);
  }

  protected moduleLabel(module: string): string {
    return uploadModuleLabel(this.i18n, module);
  }

  protected setFilter(value: string): void {
    this.filter.set(value ?? ALL_UPLOADS);
    this.page.set(0);
  }

  protected confirmDelete(upload: UploadAdminItem): void {
    this.confirm.confirm({
      header: this.i18n.t('adminSettings.uploads.deleteTitle'),
      message: this.i18n.t(upload.usageModule ? 'adminSettings.uploads.deleteUsedText' : 'adminSettings.uploads.deleteText', { name: upload.fileName }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('adminSettings.uploads.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(upload),
    });
  }
}
