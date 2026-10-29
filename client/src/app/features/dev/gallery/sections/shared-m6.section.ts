import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

import { Role, Upload, UserSummary } from '../../../../api';
import { AnimatedCounterComponent } from '../../../../shared/animated-counter/animated-counter.component';
import { ImageCropComponent } from '../../../../shared/image-crop/image-crop.component';
import { MapMarker, MapViewComponent } from '../../../../shared/map-view/map-view.component';
import { UploadDropzoneComponent } from '../../../../shared/upload-dropzone/upload-dropzone.component';
import { UserChipComponent } from '../../../../shared/user-chip/user-chip.component';
import { WallComponent, WallPost } from '../../../../shared/wall/wall.component';

const SARAH: UserSummary = { id: 'u6', displayName: 'Sarah Müller', role: Role.Foodsaver, verified: true };
const MARCO: UserSummary = {
  id: 'u12',
  displayName: 'Marco Bernasconi',
  role: Role.StoreManager,
  verified: true,
  sleepMode: true,
  sleepMessage: 'Bis Ende Monat in den Ferien',
};

/** Fixed dates far in the past so the relative dates of the screenshots stay stable. */
const POSTS: WallPost[] = [
  {
    id: 'p2',
    author: MARCO,
    body: 'Der **Fairteiler** am Helvetiaplatz ist wieder offen. Danke an alle, die geputzt haben!',
    createdAt: '2025-03-02T09:30:00Z',
    mayDelete: true,
  },
  {
    id: 'p1',
    author: SARAH,
    body: 'Wer übernimmt am Samstag die Abholung beim [Wochenmarkt](https://example.org)?',
    createdAt: '2025-03-01T16:05:00Z',
    mayDelete: false,
  },
];

const MARKERS: MapMarker[] = [
  { id: 'fsp', kind: 'fsp', lat: 47.3667, lon: 8.55, label: 'Fairteiler Kreis 8' },
  { id: 'basket', kind: 'basket', lat: 47.3801, lon: 8.53, label: 'Essenskorb: Brot und Gemüse' },
  { id: 'user', kind: 'user', lat: 47.372, lon: 8.515, label: 'Sarah Müller' },
  { id: 'store', kind: 'store', lat: 47.3901, lon: 8.5153, label: 'Bäckerei Beispiel' },
];

const FILES: Upload[] = [
  { id: 'f1', fileName: 'Hygienekonzept.pdf', mimeType: 'application/pdf', size: 248_000, url: '/api/uploads/f1' },
  { id: 'f2', fileName: 'Abholplan-Maerz.pdf', mimeType: 'application/pdf', size: 1_400_000, url: '/api/uploads/f2' },
];

/** Shared M6 components (plan-m7 Q1): sleep badge, wall, image crop, animated counter, upload dropzone, map kinds. */
@Component({
  selector: 'fs-gallery-shared-m6-section',
  imports: [AnimatedCounterComponent, ImageCropComponent, MapViewComponent, UploadDropzoneComponent, UserChipComponent, WallComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-shared-m6">Shared M6: wall, image crop, counter, uploads, map kinds</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.userChip.sleep">
        <header class="specimen__head"><code>sleep mode</code></header>
        <div class="stack">
          <fs-user-chip [name]="marco.displayName" [userId]="marco.id" [role]="marco.role" [sleepMode]="true" [sleepMessage]="marco.sleepMessage" [showRole]="true" />
          <fs-user-chip name="Lea Brunner" userId="u1" [sleepMode]="true" size="md" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.wall.posts">
        <header class="specimen__head"><code>wall (composer, posts, delete)</code></header>
        <fs-wall [posts]="posts()" [mayPost]="true" (post)="add($event)" (delete)="remove($event)" testId="gallery.wall" />
      </section>
      <section class="specimen" data-testid="gallery.wall.empty">
        <header class="specimen__head"><code>wall empty, read only</code></header>
        <fs-wall [posts]="[]" testId="gallery.wallEmpty" />
      </section>
      <section class="specimen" data-testid="gallery.imageCrop.empty">
        <header class="specimen__head"><code>image crop (pick a picture)</code></header>
        <fs-image-crop [outputSize]="400" (cropped)="croppedBytes.set($event.size)" testId="gallery.imageCrop" />
        @if (croppedBytes(); as bytes) {
          <p data-testid="gallery.imageCrop.result"><code>{{ bytes }} bytes</code></p>
        }
      </section>
      <section class="specimen" data-testid="gallery.animatedCounter.values">
        <header class="specimen__head"><code>animated counter</code></header>
        <div class="stack counters">
          <fs-animated-counter [value]="1284530" suffix=" kg" testId="gallery.counterKg" />
          <fs-animated-counter [value]="42.5" [fractionDigits]="1" testId="gallery.counterFraction" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.upload.files">
        <header class="specimen__head"><code>upload dropzone (2 files)</code></header>
        <fs-upload-dropzone [(uploads)]="uploads" [maxFiles]="3" testId="gallery.upload" />
      </section>
      <section class="specimen" data-testid="gallery.map.kinds">
        <header class="specimen__head"><code>map: fsp / basket / user / store</code></header>
        <fs-map-view [markers]="markers" height="16rem" testId="gallery.mapKinds" (bubble)="bubble.set($event.label)" />
        @if (bubble(); as label) {
          <p data-testid="gallery.map.bubble"><code>bubble: {{ label }}</code></p>
        }
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
  styles: `
    .counters { font-size: 1.6rem; font-weight: 700; }
  `,
})
export class SharedM6GallerySection {
  protected readonly marco = MARCO;
  protected readonly markers = MARKERS;
  protected readonly posts = signal<WallPost[]>(POSTS);
  protected readonly uploads = signal<Upload[]>(FILES);
  protected readonly croppedBytes = signal<number | null>(null);
  protected readonly bubble = signal<string | null>(null);

  protected add(body: string): void {
    this.posts.update((list) => [
      { id: `p${list.length + 1}`, author: SARAH, body, createdAt: '2025-03-03T08:00:00Z', mayDelete: true },
      ...list,
    ]);
  }

  protected remove(post: WallPost): void {
    this.posts.update((list) => list.filter((item) => item.id !== post.id));
  }
}

export const SHARED_M6_GALLERY_SECTIONS = [SharedM6GallerySection];
