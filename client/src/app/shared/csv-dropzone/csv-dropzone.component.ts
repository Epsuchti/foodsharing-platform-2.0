import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

import { TPipe } from '../../core/i18n/t.pipe';

/** A CSV file read in the browser (sent as text in the request body, e.g. `RegionsCsvImport.csv`). */
export interface CsvFile {
  name: string;
  size: number;
  text: string;
  /** Data rows (lines without the header and empty lines). */
  rows: number;
}

let nextId = 0;

/**
 * Drop zone / file picker for CSV imports (regions, past intro pickups). Reads the file as UTF-8 text, rejects
 * other types and files above `maxBytes`, and emits the text with a row count. The server validates the content
 * (dry run / preview).
 */
@Component({
  selector: 'fs-csv-dropzone',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-csv-dropzone' },
  template: `
    <label
      class="zone"
      [class.zone--over]="dragOver()"
      [class.zone--error]="error()"
      [for]="inputId"
      (dragover)="onDragOver($event)"
      (dragleave)="dragOver.set(false)"
      (drop)="onDrop($event)"
      [attr.data-testid]="testId()"
    >
      <i class="pi pi-file-import zone__icon" aria-hidden="true"></i>
      @if (file(); as current) {
        <span class="zone__title" [attr.data-testid]="testId() + '.file'">{{ current.name }}</span>
        <span class="zone__text">
          {{ 'shared.csv.loaded' | t: { rows: current.rows, size: sizeLabel(current.size) } }}
        </span>
      } @else {
        <span class="zone__title">{{ 'shared.csv.drop' | t }}</span>
        <span class="zone__text">{{ 'shared.csv.or' | t }}</span>
      }
      <span class="fs-btn fs-btn--outlined fs-btn--sm zone__pick">
        <i class="pi pi-folder-open" aria-hidden="true"></i>
        {{ (file() ? 'shared.csv.replace' : 'shared.csv.choose') | t }}
      </span>
      <input
        class="fs-visually-hidden"
        type="file"
        accept=".csv,text/csv"
        [id]="inputId"
        (change)="onPick($event)"
        [attr.data-testid]="testId() + '.input'"
      />
    </label>
    @if (error(); as message) {
      <p class="error" role="alert" [attr.data-testid]="testId() + '.error'">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
        {{ message | t: { max: sizeLabel(maxBytes()) } }}
      </p>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.5rem;
    }
    .zone {
      display: grid;
      justify-items: center;
      gap: 0.35rem;
      padding: 1.5rem 1rem;
      border: 2px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface-muted);
      text-align: center;
      cursor: pointer;
      transition:
        border-color 150ms ease,
        background-color 150ms ease;
    }
    .zone:hover,
    .zone--over,
    .zone:focus-within {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
    }
    .zone--error {
      border-color: var(--fs-danger);
    }
    .zone__icon {
      color: var(--fs-primary-strong);
      font-size: 1.8rem;
    }
    .zone__title {
      font-weight: 700;
      overflow-wrap: anywhere;
    }
    .zone__text {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .zone__pick {
      margin-top: 0.4rem;
    }
    .error {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      margin: 0;
      color: var(--fs-danger);
      font-size: 0.9rem;
      font-weight: 600;
    }
  `,
})
export class CsvDropzoneComponent {
  /** Default 2 MB (5000 rows of the import formats stay far below). */
  readonly maxBytes = input(2 * 1024 * 1024);
  readonly testId = input('shared.csv');
  readonly fileChange = output<CsvFile>();

  protected readonly inputId = `fs-csv-${nextId++}`;
  protected readonly dragOver = signal(false);
  protected readonly file = signal<CsvFile | null>(null);
  protected readonly error = signal<string | null>(null);

  protected onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  protected onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      void this.read(file);
    }
  }

  protected onPick(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    if (file) {
      void this.read(file);
    }
    target.value = '';
  }

  /** Reads and checks a file (public for tests and drag sources outside the zone). */
  async read(file: File): Promise<void> {
    this.error.set(null);
    if (!/\.csv$/i.test(file.name) && !/csv|text\/plain/.test(file.type)) {
      this.error.set('shared.csv.notCsv');
      return;
    }
    if (file.size > this.maxBytes()) {
      this.error.set('shared.csv.tooLarge');
      return;
    }
    // drop a UTF-8 byte order mark (Excel exports)
    const raw = await file.text();
    const text = raw.charCodeAt(0) === 0xfeff ? raw.slice(1) : raw;
    const rows = Math.max(0, text.split(/\r?\n/).filter((line) => line.trim()).length - 1);
    const csv: CsvFile = { name: file.name, size: file.size, text, rows };
    this.file.set(csv);
    this.fileChange.emit(csv);
  }

  protected sizeLabel(bytes: number): string {
    return bytes >= 1024 * 1024
      ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
      : `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
}
