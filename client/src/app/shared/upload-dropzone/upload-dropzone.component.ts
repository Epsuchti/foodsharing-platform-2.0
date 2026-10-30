import { ChangeDetectionStrategy, Component, computed, inject, input, model, signal } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { Upload, UploadsService } from '../../api';
import { errorParams } from '../../core/data/api-error';
import { runMutation } from '../../core/data/mutation';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';

/** A file that could not be uploaded (shown with its reason until removed). */
export interface FailedUpload {
  key: number;
  fileName: string;
  /** Translated reason. */
  message: string;
}

let nextId = 0;
let nextKey = 0;

export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/**
 * Multi-file upload (M6 support attachments, food-share point pictures, blog images): drop or pick files, each is
 * sent to `createUpload` (one after the other), the list shows uploaded files with remove and failed ones with the
 * reason. `uploads` is a two-way model of the uploaded files (their ids go into the host's request).
 *
 * `<fs-upload-dropzone [(uploads)]="attachments" [maxFiles]="5" accept="image/*,application/pdf" testId="support.files" />`
 */
@Component({
  selector: 'fs-upload-dropzone',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-upload-dropzone' },
  template: `
    @if (!full()) {
      <label
        class="zone"
        [class.zone--over]="dragOver()"
        [for]="inputId"
        (dragover)="onDragOver($event)"
        (dragleave)="dragOver.set(false)"
        (drop)="onDrop($event)"
        [attr.data-testid]="testId()"
      >
        <i class="pi pi-cloud-upload zone__icon" aria-hidden="true"></i>
        <span class="zone__title">{{ 'shared.upload.drop' | t }}</span>
        <span class="zone__text">{{ 'shared.upload.limits' | t: { max: maxFiles(), size: sizeLabel(maxBytes()) } }}</span>
        <span class="fs-btn fs-btn--outlined fs-btn--sm zone__pick">
          <i class="pi pi-folder-open" aria-hidden="true"></i>
          {{ 'shared.upload.choose' | t }}
        </span>
        <input
          class="fs-visually-hidden"
          type="file"
          multiple
          [accept]="accept()"
          [id]="inputId"
          (change)="onPick($event)"
          [attr.data-testid]="testId() + '.input'"
        />
      </label>
    } @else {
      <p class="muted" [attr.data-testid]="testId() + '.full'">{{ 'shared.upload.full' | t: { max: maxFiles() } }}</p>
    }

    @if (uploading(); as name) {
      <p class="status" role="status" [attr.data-testid]="testId() + '.uploading'">
        <i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'shared.upload.uploading' | t: { name } }}
      </p>
    }

    @if (uploads().length || failed().length) {
      <ul class="files" [attr.data-testid]="testId() + '.list'">
        @for (upload of uploads(); track upload.id) {
          <li class="file" [attr.data-testid]="testId() + '.file'" [attr.data-upload-id]="upload.id">
            @if (isImage(upload)) {
              <img class="file__thumb" [src]="upload.url" alt="" />
            } @else {
              <i class="pi pi-file file__icon" aria-hidden="true"></i>
            }
            <span class="file__name">{{ upload.fileName }}</span>
            <!-- files attached earlier (e.g. resource images) come back without a size -->
            <span class="muted">{{ upload.size ? sizeLabel(upload.size) : '' }}</span>
            <p-button
              icon="pi pi-times"
              [text]="true"
              [rounded]="true"
              size="small"
              severity="secondary"
              [ariaLabel]="'shared.upload.remove' | t: { name: upload.fileName }"
              (onClick)="remove(upload)"
              [attr.data-testid]="testId() + '.remove'"
            />
          </li>
        }
        @for (failure of failed(); track failure.key) {
          <li class="file file--failed" [attr.data-testid]="testId() + '.failed'">
            <i class="pi pi-exclamation-triangle file__icon" aria-hidden="true"></i>
            <span class="file__name">{{ failure.fileName }}</span>
            <span class="file__error">{{ failure.message }}</span>
            <p-button
              icon="pi pi-times"
              [text]="true"
              [rounded]="true"
              size="small"
              severity="secondary"
              [ariaLabel]="'shared.upload.dismiss' | t: { name: failure.fileName }"
              (onClick)="dismiss(failure)"
            />
          </li>
        }
      </ul>
    } @else if (!uploading()) {
      <p class="muted" [attr.data-testid]="testId() + '.empty'">{{ 'shared.upload.empty' | t }}</p>
    }
  `,
  styles: `
    :host { display: grid; gap: 0.6rem; min-width: 0; }
    .zone {
      display: grid;
      justify-items: center;
      gap: 0.35rem;
      padding: 1.25rem 1rem;
      border: 2px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface-muted);
      text-align: center;
      cursor: pointer;
    }
    .zone:hover, .zone--over, .zone:focus-within { border-color: var(--fs-primary); background: var(--fs-primary-soft); }
    .zone__icon { color: var(--fs-primary-strong); font-size: 1.8rem; }
    .zone__title { font-weight: 700; }
    .zone__text, .muted { color: var(--fs-text-muted); font-size: 0.88rem; }
    .muted { margin: 0; }
    .zone__pick { margin-top: 0.4rem; }
    .status { display: flex; align-items: center; gap: 0.4rem; margin: 0; font-size: 0.9rem; }
    .files { display: grid; gap: 0.4rem; margin: 0; padding: 0; list-style: none; }
    .file {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 0.6rem;
      padding: 0.4rem 0.4rem 0.4rem 0.6rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
    }
    .file--failed { border-color: var(--fs-danger); }
    .file__thumb { width: 2.25rem; height: 2.25rem; border-radius: var(--fs-radius-sm); object-fit: cover; }
    .file__icon { width: 2.25rem; text-align: center; color: var(--fs-text-muted); font-size: 1.2rem; }
    .file--failed .file__icon, .file__error { color: var(--fs-danger); }
    .file__name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
    .file__error { font-size: 0.85rem; font-weight: 600; }
  `,
})
export class UploadDropzoneComponent {
  private readonly api = inject(UploadsService);
  private readonly i18n = inject(I18nService);

  /** Uploaded files (two-way). */
  readonly uploads = model<Upload[]>([]);
  readonly maxFiles = input(5);
  /** Per file (default 10 MB; the server has its own limit). */
  readonly maxBytes = input(10 * 1024 * 1024);
  readonly accept = input('image/*,application/pdf');
  readonly testId = input('shared.upload');

  protected readonly inputId = `fs-upload-${nextId++}`;
  protected readonly dragOver = signal(false);
  protected readonly failed = signal<FailedUpload[]>([]);
  /** Name of the file being uploaded. */
  protected readonly uploading = signal<string | null>(null);
  protected readonly full = computed(() => this.uploads().length >= this.maxFiles());

  private readonly send = runMutation((file: File) => this.api.createUpload({ file }), {
    errorToast: false,
    onSuccess: (upload) => this.uploads.update((list) => [...list, upload]),
  });

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    void this.add(Array.from(event.dataTransfer?.files ?? []));
  }

  protected onPick(event: Event): void {
    const target = event.target as HTMLInputElement;
    const files = Array.from(target.files ?? []);
    target.value = '';
    void this.add(files);
  }

  /** Uploads the files one after the other (public for tests and other drop targets). */
  async add(files: File[]): Promise<void> {
    for (const file of files) {
      if (this.uploads().length >= this.maxFiles()) {
        this.fail(file.name, this.i18n.t('shared.upload.tooMany', { max: this.maxFiles() }));
        continue;
      }
      if (file.size > this.maxBytes()) {
        this.fail(file.name, this.i18n.t('shared.upload.tooLarge', { size: formatBytes(this.maxBytes()) }));
        continue;
      }
      this.uploading.set(file.name);
      const ok = await this.send.run(file);
      if (!ok) {
        const error = this.send.error();
        this.fail(file.name, error ? this.i18n.error(error.code, errorParams(error)) : this.i18n.t('shared.upload.failed'));
      }
    }
    this.uploading.set(null);
  }

  remove(upload: Upload): void {
    this.uploads.update((list) => list.filter((item) => item.id !== upload.id));
  }

  protected dismiss(failure: FailedUpload): void {
    this.failed.update((list) => list.filter((item) => item.key !== failure.key));
  }

  protected isImage(upload: Upload): boolean {
    return upload.mimeType.startsWith('image/');
  }

  protected sizeLabel(bytes: number): string {
    return formatBytes(bytes);
  }

  private fail(fileName: string, message: string): void {
    this.failed.update((list) => [...list, { key: nextKey++, fileName, message }]);
  }
}
