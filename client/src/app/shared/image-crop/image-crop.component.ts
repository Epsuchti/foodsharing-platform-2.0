import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { TPipe } from '../../core/i18n/t.pipe';

/** Square crop in source image pixels. */
export interface CropRect {
  sx: number;
  sy: number;
  size: number;
}

export const MAX_ZOOM = 4;
export const DEFAULT_MAX_BYTES = 1024 * 1024;
const QUALITIES = [0.92, 0.85, 0.75, 0.65, 0.55, 0.45];

/**
 * The square crop for an image of `width`×`height` at `zoom` (1 = the largest square that fits) whose centre is
 * moved by `offsetX`/`offsetY` image pixels from the image centre; the square always stays inside the image.
 */
export function cropRect(width: number, height: number, zoom: number, offsetX = 0, offsetY = 0): CropRect {
  const size = Math.min(width, height) / Math.min(Math.max(zoom, 1), MAX_ZOOM);
  const clamp = (value: number, max: number) => Math.min(Math.max(value, 0), max);
  return {
    sx: clamp(width / 2 + offsetX - size / 2, width - size),
    sy: clamp(height / 2 + offsetY - size / 2, height - size),
    size,
  };
}

/** Offsets that keep the crop inside the image (so dragging beyond an edge does not pile up). */
export function clampOffset(width: number, height: number, zoom: number, offsetX: number, offsetY: number): [number, number] {
  const rect = cropRect(width, height, zoom, offsetX, offsetY);
  return [rect.sx + rect.size / 2 - width / 2, rect.sy + rect.size / 2 - height / 2];
}

/** Something that encodes itself as a blob (an HTMLCanvasElement, or a fake in tests). */
export interface BlobSource {
  toBlob(callback: (blob: Blob | null) => void, type?: string, quality?: number): void;
}

function toJpeg(canvas: BlobSource, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
}

/**
 * Encodes `render(size)` as JPEG not larger than `maxBytes`: lowers the quality first, then halves the pixel size
 * (down to 64 px). Resolves null when the browser cannot encode.
 */
export async function encodeJpeg(
  render: (size: number) => BlobSource,
  outputSize: number,
  maxBytes = DEFAULT_MAX_BYTES,
): Promise<Blob | null> {
  let last: Blob | null = null;
  for (let size = outputSize; size >= 64; size = Math.floor(size / 2)) {
    const canvas = render(size);
    for (const quality of QUALITIES) {
      const blob = await toJpeg(canvas, quality);
      if (!blob) {
        return null;
      }
      last = blob;
      if (blob.size <= maxBytes) {
        return blob;
      }
    }
  }
  return last && last.size <= maxBytes ? last : null;
}

interface LoadedImage {
  image: HTMLImageElement;
  width: number;
  height: number;
  url: string;
}

let nextId = 0;

/**
 * Picture picker with a square crop (M6 profile photo, food-share point pictures): choose an image, drag to move
 * and zoom with the slider (or the buttons / mouse wheel), then "Use picture" emits a square JPEG blob of
 * `outputSize` px, at most `maxBytes` (quality and size are lowered until it fits). No extra dependency: the
 * preview and the result are drawn on a canvas.
 */
@Component({
  selector: 'fs-image-crop',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-image-crop', '[attr.data-testid]': 'testId()' },
  template: `
    <input
      #fileInput
      class="fs-visually-hidden"
      type="file"
      accept="image/jpeg,image/png,image/webp,image/gif"
      [id]="inputId"
      (change)="onPick($event)"
      [attr.data-testid]="testId() + '.input'"
    />
    @if (loaded()) {
      <div
        class="stage"
        role="img"
        tabindex="0"
        [attr.aria-label]="'shared.imageCrop.stage' | t"
        (pointerdown)="onPointerDown($event)"
        (pointermove)="onPointerMove($event)"
        (pointerup)="dragging.set(false)"
        (pointercancel)="dragging.set(false)"
        (wheel)="onWheel($event)"
        (keydown)="onKey($event)"
        [class.stage--dragging]="dragging()"
        [attr.data-testid]="testId() + '.stage'"
      >
        <canvas #preview [width]="stagePx" [height]="stagePx"></canvas>
      </div>
      <div class="zoom">
        <p-button icon="pi pi-search-minus" [text]="true" [rounded]="true" size="small" [ariaLabel]="'shared.imageCrop.zoomOut' | t" (onClick)="setZoom(zoom() - 0.25)" />
        <input
          class="zoom__slider"
          type="range"
          min="1"
          [max]="maxZoom"
          step="0.05"
          [value]="zoom()"
          (input)="setZoom(+$any($event.target).value)"
          [attr.aria-label]="'shared.imageCrop.zoom' | t"
          [attr.data-testid]="testId() + '.zoom'"
        />
        <p-button icon="pi pi-search-plus" [text]="true" [rounded]="true" size="small" [ariaLabel]="'shared.imageCrop.zoomIn' | t" (onClick)="setZoom(zoom() + 0.25)" />
      </div>
      <p class="hint">{{ 'shared.imageCrop.hint' | t }}</p>
    } @else {
      <label class="empty" [for]="inputId" [attr.data-testid]="testId() + '.pick'">
        <i class="pi pi-image empty__icon" aria-hidden="true"></i>
        <span class="empty__title">{{ 'shared.imageCrop.choose' | t }}</span>
        <span class="hint">{{ 'shared.imageCrop.formats' | t }}</span>
      </label>
    }
    @if (error(); as key) {
      <p class="error" role="alert" [attr.data-testid]="testId() + '.error'">
        <i class="pi pi-exclamation-triangle" aria-hidden="true"></i> {{ key | t }}
      </p>
    }
    @if (loaded()) {
      <div class="actions">
        <label class="fs-btn fs-btn--outlined fs-btn--sm" [for]="inputId">
          <i class="pi pi-folder-open" aria-hidden="true"></i> {{ 'shared.imageCrop.replace' | t }}
        </label>
        <p-button
          [label]="'shared.imageCrop.apply' | t"
          icon="pi pi-check"
          size="small"
          [loading]="encoding()"
          (onClick)="apply()"
          [attr.data-testid]="testId() + '.apply'"
        />
      </div>
    }
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; justify-items: center; }
    .stage {
      width: min(16rem, 100%);
      aspect-ratio: 1;
      overflow: hidden;
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface-muted);
      cursor: grab;
      touch-action: none;
      outline-offset: 3px;
    }
    .stage--dragging { cursor: grabbing; }
    .stage canvas { display: block; width: 100%; height: 100%; }
    .zoom { display: flex; align-items: center; gap: 0.5rem; width: min(18rem, 100%); }
    .zoom__slider { flex: 1; accent-color: var(--fs-primary); }
    .empty {
      display: grid;
      justify-items: center;
      gap: 0.35rem;
      width: min(16rem, 100%);
      aspect-ratio: 1;
      align-content: center;
      border: 2px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface-muted);
      cursor: pointer;
      text-align: center;
      padding: 1rem;
    }
    .empty:hover, .empty:focus-within { border-color: var(--fs-primary); background: var(--fs-primary-soft); }
    .empty__icon { color: var(--fs-primary-strong); font-size: 2rem; }
    .empty__title { font-weight: 700; }
    .hint { margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; text-align: center; }
    .error { display: flex; align-items: center; gap: 0.4rem; margin: 0; color: var(--fs-danger); font-weight: 600; font-size: 0.9rem; }
    .actions { display: flex; flex-wrap: wrap; gap: 0.5rem; justify-content: center; }
  `,
})
export class ImageCropComponent {
  /** Edge length of the result in px. */
  readonly outputSize = input(800);
  readonly maxBytes = input(DEFAULT_MAX_BYTES);
  /** Largest accepted source file (default 15 MB). */
  readonly maxSourceBytes = input(15 * 1024 * 1024);
  readonly testId = input('shared.imageCrop');
  readonly cropped = output<Blob>();

  protected readonly inputId = `fs-image-crop-${nextId++}`;
  protected readonly maxZoom = MAX_ZOOM;
  protected readonly stagePx = 512;
  protected readonly loaded = signal<LoadedImage | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly encoding = signal(false);
  protected readonly dragging = signal(false);
  readonly zoom = signal(1);
  readonly offset = signal<[number, number]>([0, 0]);
  readonly crop = computed(() => {
    const image = this.loaded();
    const [x, y] = this.offset();
    return image ? cropRect(image.width, image.height, this.zoom(), x, y) : null;
  });

  private readonly preview = viewChild<ElementRef<HTMLCanvasElement>>('preview');
  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
  private lastPointer: [number, number] | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.release());
    effect(() => {
      const canvas = this.preview()?.nativeElement;
      const image = this.loaded();
      const crop = this.crop();
      if (canvas && image && crop) {
        draw(canvas, image.image, crop, this.stagePx);
      }
    });
  }

  /** Opens the file dialog. */
  choose(): void {
    this.fileInput().nativeElement.click();
  }

  protected onPick(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    target.value = '';
    if (file) {
      void this.load(file);
    }
  }

  /** Reads and decodes a picture (public for tests and drop targets). */
  async load(file: File): Promise<void> {
    this.error.set(null);
    if (!file.type.startsWith('image/')) {
      this.error.set('shared.imageCrop.notImage');
      return;
    }
    if (file.size > this.maxSourceBytes()) {
      this.error.set('shared.imageCrop.tooLarge');
      return;
    }
    const url = URL.createObjectURL(file);
    try {
      const image = new Image();
      image.src = url;
      await image.decode();
      this.release();
      this.loaded.set({ image, width: image.naturalWidth, height: image.naturalHeight, url });
      this.zoom.set(1);
      this.offset.set([0, 0]);
    } catch {
      URL.revokeObjectURL(url);
      this.error.set('shared.imageCrop.unreadable');
    }
  }

  setZoom(value: number): void {
    const image = this.loaded();
    const zoom = Math.min(Math.max(value, 1), MAX_ZOOM);
    this.zoom.set(zoom);
    if (image) {
      const [x, y] = this.offset();
      this.offset.set(clampOffset(image.width, image.height, zoom, x, y));
    }
  }

  /** Moves the crop by a drag of `dx`/`dy` stage px (the picture follows the pointer). */
  moveBy(dx: number, dy: number, stageWidth: number): void {
    const image = this.loaded();
    const crop = this.crop();
    if (!image || !crop || stageWidth <= 0) {
      return;
    }
    const factor = crop.size / stageWidth;
    const [x, y] = this.offset();
    this.offset.set(clampOffset(image.width, image.height, this.zoom(), x - dx * factor, y - dy * factor));
  }

  protected onPointerDown(event: PointerEvent): void {
    (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
    this.lastPointer = [event.clientX, event.clientY];
    this.dragging.set(true);
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.dragging() || !this.lastPointer) {
      return;
    }
    const [lx, ly] = this.lastPointer;
    this.lastPointer = [event.clientX, event.clientY];
    this.moveBy(event.clientX - lx, event.clientY - ly, (event.currentTarget as HTMLElement).clientWidth);
  }

  protected onWheel(event: WheelEvent): void {
    event.preventDefault();
    this.setZoom(this.zoom() + (event.deltaY < 0 ? 0.1 : -0.1));
  }

  protected onKey(event: KeyboardEvent): void {
    const step = 16;
    const moves: Record<string, [number, number]> = {
      ArrowLeft: [step, 0],
      ArrowRight: [-step, 0],
      ArrowUp: [0, step],
      ArrowDown: [0, -step],
    };
    const move = moves[event.key];
    if (move) {
      event.preventDefault();
      this.moveBy(move[0], move[1], (event.currentTarget as HTMLElement).clientWidth || this.stagePx);
    } else if (event.key === '+' || event.key === '-') {
      event.preventDefault();
      this.setZoom(this.zoom() + (event.key === '+' ? 0.25 : -0.25));
    }
  }

  /** Encodes the crop and emits it. */
  async apply(): Promise<void> {
    const image = this.loaded();
    const crop = this.crop();
    if (!image || !crop || this.encoding()) {
      return;
    }
    this.encoding.set(true);
    this.error.set(null);
    try {
      const size = Math.min(this.outputSize(), Math.round(crop.size));
      const blob = await encodeJpeg(
        (px) => {
          const canvas = document.createElement('canvas');
          canvas.width = px;
          canvas.height = px;
          draw(canvas, image.image, crop, px);
          return canvas;
        },
        Math.max(size, 64),
        this.maxBytes(),
      );
      if (blob) {
        this.cropped.emit(blob);
      } else {
        this.error.set('shared.imageCrop.encodeFailed');
      }
    } finally {
      this.encoding.set(false);
    }
  }

  private release(): void {
    const current = this.loaded();
    if (current) {
      URL.revokeObjectURL(current.url);
    }
  }
}

function draw(canvas: HTMLCanvasElement, image: CanvasImageSource, crop: CropRect, px: number): void {
  const context = canvas.getContext('2d');
  if (!context) {
    return;
  }
  context.fillStyle = '#ffffff';
  context.fillRect(0, 0, px, px);
  context.imageSmoothingQuality = 'high';
  context.drawImage(image, crop.sx, crop.sy, crop.size, crop.size, 0, 0, px, px);
}
