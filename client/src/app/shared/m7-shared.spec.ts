import { HttpTestingController } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';

import { ApiErrorCode, Role, Upload } from '../api';
import { DataState, loadResource } from '../core/data/data-state';
import { apiHttpError, provideDataStateTesting } from '../core/testing/data-state-testing';
import { ANIMATION_FRAMES, AnimatedCounterComponent, easeOutCubic } from './animated-counter/animated-counter.component';
import { BlobSource, ImageCropComponent, clampOffset, cropRect, encodeJpeg } from './image-crop/image-crop.component';
import { MapMarker, MapViewComponent } from './map-view/map-view.component';
import { UploadDropzoneComponent } from './upload-dropzone/upload-dropzone.component';
import { UserAvatarComponent } from './user-chip/user-avatar.component';
import { UserChipComponent } from './user-chip/user-chip.component';
import { WallComponent, WallPost } from './wall/wall.component';

/** Specs of the shared M6 components prepared in M7 Q1. */

beforeEach(() => {
  TestBed.configureTestingModule({
    providers: [
      ...provideDataStateTesting({
        translations: {
          'shared.userChip.sleeping': 'Macht Pause',
          'shared.userChip.sleepingWithMessage': 'Macht Pause: {message}',
          'shared.wall.empty': 'Noch keine Beiträge',
          'shared.upload.empty': 'Keine Dateien',
          'shared.upload.tooLarge': 'Grösser als {size}',
          'shared.upload.uploading': 'Lade {name}',
        },
      }),
      provideOptimus(),
    ],
  });
});

const el = (fixture: { nativeElement: unknown }) => fixture.nativeElement as HTMLElement;
const byTestId = (root: HTMLElement, id: string) => root.querySelectorAll(`[data-testid="${id}"]`);

describe('UserChipComponent sleep badge', () => {
  it('shows the moon with the sleep message and a grey avatar ring only in sleep mode', async () => {
    const fixture = TestBed.createComponent(UserChipComponent);
    fixture.componentRef.setInput('name', 'Marco');
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.userChip.sleep')).toHaveLength(0);
    expect(el(fixture).querySelector('fs-user-avatar')!.hasAttribute('data-sleeping')).toBe(false);

    fixture.componentRef.setInput('sleepMode', true);
    fixture.componentRef.setInput('sleepMessage', 'In den Ferien');
    await fixture.whenStable();
    const moon = el(fixture).querySelector('[data-testid="shared.userChip.sleep"]')!;
    expect(moon.classList.contains('pi-moon')).toBe(true);
    expect(moon.getAttribute('aria-label')).toBe('Macht Pause: In den Ferien');
    expect(el(fixture).querySelector('fs-user-avatar')!.hasAttribute('data-sleeping')).toBe(true);

    fixture.componentRef.setInput('sleepMessage', undefined);
    await fixture.whenStable();
    expect(moon.getAttribute('aria-label')).toBe('Macht Pause');

    // enum form of UserSummary.sleepMode
    fixture.componentRef.setInput('sleepMode', 'NONE');
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.userChip.sleep')).toHaveLength(0);
    fixture.componentRef.setInput('sleepMode', 'TEMPORARY');
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.userChip.sleep')).toHaveLength(1);
  });
});

describe('UserAvatarComponent broken image', () => {
  it('falls back to initials and tries again when the photo URL changes (Q3 photo upload)', async () => {
    const fixture = TestBed.createComponent(UserAvatarComponent);
    fixture.componentRef.setInput('name', 'Sarah Müller');
    fixture.componentRef.setInput('avatarUrl', '/api/uploads/old?size=md');
    await fixture.whenStable();
    el(fixture).querySelector('img')!.dispatchEvent(new Event('error'));
    await fixture.whenStable();
    expect(el(fixture).querySelector('img')).toBeNull();
    expect(el(fixture).textContent).toContain('SM');
    fixture.componentRef.setInput('avatarUrl', '/api/uploads/new?size=md');
    await fixture.whenStable();
    expect(el(fixture).querySelector('img')?.getAttribute('src')).toBe('/api/uploads/new?size=md');
  });
});

describe('WallComponent', () => {
  const post = (over: Partial<WallPost>): WallPost => ({
    id: 'p1',
    author: { id: 'u1', displayName: 'Sarah', role: Role.Foodsaver, verified: true },
    body: 'Hallo **Welt**',
    createdAt: '2026-10-01T10:00:00Z',
    mayDelete: false,
    ...over,
  });

  it('renders posts with Markdown, empty text without posts and delete only where allowed', async () => {
    const fixture = TestBed.createComponent(WallComponent);
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.wall.empty')[0].textContent).toContain('Noch keine Beiträge');

    fixture.componentRef.setInput('posts', [post({ id: 'a', mayDelete: true }), post({ id: 'b' })]);
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.wall.post')).toHaveLength(2);
    expect(el(fixture).querySelector('[data-testid="shared.wall.body"] strong')?.textContent).toBe('Welt');
    expect(byTestId(el(fixture), 'shared.wall.delete')).toHaveLength(1);
    expect(el(fixture).querySelector('time')?.getAttribute('datetime')).toBe('2026-10-01T10:00:00Z');
    expect(byTestId(el(fixture), 'shared.wall.composer')).toHaveLength(0);
  });

  it('emits the post and clears the draft only after the posts changed; delete asks first', async () => {
    const fixture = TestBed.createComponent(WallComponent);
    fixture.componentRef.setInput('mayPost', true);
    fixture.componentRef.setInput('posts', [post({ mayDelete: true })]);
    const posted: string[] = [];
    const deleted: WallPost[] = [];
    fixture.componentInstance.post.subscribe((body) => posted.push(body));
    fixture.componentInstance.delete.subscribe((item) => deleted.push(item));
    await fixture.whenStable();

    const text = el(fixture).querySelector('[data-testid="shared.wall.text"]') as HTMLTextAreaElement;
    text.value = '  Neuer Beitrag  ';
    text.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    (el(fixture).querySelector('[data-testid="shared.wall.composer"]') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(posted).toEqual(['Neuer Beitrag']);
    expect(text.value).toBe('  Neuer Beitrag  '); // kept until the host shows the new post

    fixture.componentRef.setInput('posts', [post({ id: 'new', body: 'Neuer Beitrag' }), post({ mayDelete: true })]);
    await fixture.whenStable();
    expect(text.value).toBe('');

    const confirm = TestBed.inject(ConfirmationService);
    const spy = vi.spyOn(confirm, 'confirm').mockImplementation((options) => {
      options.accept?.();
      return confirm;
    });
    (el(fixture).querySelector('[data-testid="shared.wall.delete"] button') as HTMLElement).click();
    expect(spy).toHaveBeenCalled();
    expect(deleted.map((item) => item.id)).toEqual(['p1']);
  });

  it('shows loading and error with retry through its DataState', async () => {
    @Component({
      imports: [WallComponent],
      changeDetection: ChangeDetectionStrategy.OnPush,
      template: `<fs-wall [posts]="state.data() ?? []" [state]="state" />`,
    })
    class Host {
      readonly state: DataState<WallPost[]> = loadResource({ loader: () => Promise.reject(apiHttpError(500, ApiErrorCode.UnexpectedError)) });
    }
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    await vi.waitFor(() => expect(el(fixture).querySelector('[data-testid="shared.wall.state"]')?.getAttribute('data-state')).toBe('error'));
  });
});

describe('AnimatedCounterComponent', () => {
  const frames: FrameRequestCallback[] = [];
  beforeEach(() => {
    frames.length = 0;
    TestBed.overrideProvider(ANIMATION_FRAMES, {
      useValue: { request: (callback: FrameRequestCallback) => frames.push(callback), cancel: () => undefined },
    });
  });
  afterEach(() => vi.restoreAllMocks());

  it('shows the final value at once with prefers-reduced-motion', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({ matches: query.includes('reduce'), media: query }) as MediaQueryList);
    const fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 1234);
    await fixture.whenStable();
    expect(fixture.componentInstance.shown()).toBe(1234);
    expect(frames).toHaveLength(0);
    expect(el(fixture).getAttribute('data-value')).toBe('1234');
    // formatted for the UI language (de-CH groups with an apostrophe); screen readers get the final value once
    expect(el(fixture).querySelector('[aria-hidden="true"]')?.textContent).toMatch(/^1\D?234$/);
    expect(el(fixture).querySelector('.fs-visually-hidden')?.textContent).toMatch(/^1\D?234$/);
  });

  it('counts up frame by frame otherwise', async () => {
    vi.spyOn(window, 'matchMedia').mockImplementation((query: string) => ({ matches: false, media: query }) as MediaQueryList);
    const fixture = TestBed.createComponent(AnimatedCounterComponent);
    fixture.componentRef.setInput('value', 100);
    fixture.componentRef.setInput('duration', 1000);
    await fixture.whenStable();
    expect(fixture.componentInstance.shown()).toBe(0);
    frames.shift()!(0);
    frames.shift()!(500);
    expect(fixture.componentInstance.shown()).toBeCloseTo(100 * easeOutCubic(0.5));
    frames.shift()!(1000);
    expect(fixture.componentInstance.shown()).toBe(100);
    expect(frames).toHaveLength(0);
  });
});

describe('image crop', () => {
  it('computes a square crop inside the image for zoom and offsets', () => {
    expect(cropRect(1200, 800, 1)).toEqual({ sx: 200, sy: 0, size: 800 });
    expect(cropRect(1200, 800, 2)).toEqual({ sx: 400, sy: 200, size: 400 });
    // moved far to the right/bottom: clamped to the image edge
    expect(cropRect(1200, 800, 2, 5000, 5000)).toEqual({ sx: 800, sy: 400, size: 400 });
    expect(cropRect(1200, 800, 99).size).toBe(200); // max zoom 4
    expect(clampOffset(1200, 800, 1, 999, 999)).toEqual([200, 0]);
  });

  it('encodes a JPEG within the byte limit by lowering quality, then the pixel size', async () => {
    const rendered: number[] = [];
    const render = (size: number): BlobSource => {
      rendered.push(size);
      return {
        toBlob: (callback, type, quality = 1) =>
          callback(new Blob([new Uint8Array(Math.round(size * size * quality * 2))], { type })),
      };
    };
    const blob = await encodeJpeg(render, 800, 400_000);
    expect(blob).not.toBeNull();
    expect(blob!.type).toBe('image/jpeg');
    expect(blob!.size).toBeLessThanOrEqual(400_000);
    expect(rendered).toEqual([800, 400]);

    expect(await encodeJpeg(() => ({ toBlob: (callback) => callback(null) }), 800)).toBeNull();
  });

  it('rejects files that are not pictures', async () => {
    const fixture = TestBed.createComponent(ImageCropComponent);
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.imageCrop.pick')).toHaveLength(1);
    await fixture.componentInstance.load(new File(['a,b'], 'list.csv', { type: 'text/csv' }));
    await fixture.whenStable();
    expect(byTestId(el(fixture), 'shared.imageCrop.error')).toHaveLength(1);
  });
});

describe('UploadDropzoneComponent', () => {
  const upload = (id: string, fileName: string): Upload => ({ id, fileName, mimeType: 'application/pdf', size: 2048, url: `/u/${id}` });

  it('uploads files one after the other, lists them with remove and shows failures', async () => {
    @Component({
      imports: [UploadDropzoneComponent],
      changeDetection: ChangeDetectionStrategy.OnPush,
      template: `<fs-upload-dropzone [(uploads)]="uploads" [maxFiles]="2" [maxBytes]="10000" testId="t.up" />`,
    })
    class Host {
      readonly uploads = signal<Upload[]>([]);
    }
    const fixture = TestBed.createComponent(Host);
    const http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    expect(byTestId(el(fixture), 't.up.empty')[0].textContent).toContain('Keine Dateien');

    const dropzone = fixture.debugElement.children[0].componentInstance as UploadDropzoneComponent;
    const done = dropzone.add([
      new File(['a'], 'a.pdf', { type: 'application/pdf' }),
      new File(['b'], 'b.pdf', { type: 'application/pdf' }),
      new File([new Uint8Array(20000)], 'big.pdf', { type: 'application/pdf' }),
    ]);
    await fixture.whenStable();
    expect(byTestId(el(fixture), 't.up.uploading')[0].textContent).toContain('Lade a.pdf');
    http.expectOne('/api/uploads').flush(upload('1', 'a.pdf'));
    await vi.waitFor(() => http.expectOne('/api/uploads').flush({ code: 'UNEXPECTED_ERROR', message: 'x', retryable: true }, { status: 500, statusText: 'x' }));
    await done;
    await fixture.whenStable();

    expect(fixture.componentInstance.uploads().map((item) => item.fileName)).toEqual(['a.pdf']);
    expect(byTestId(el(fixture), 't.up.file')).toHaveLength(1);
    const failed = byTestId(el(fixture), 't.up.failed');
    expect(failed).toHaveLength(2);
    expect(failed[1].textContent).toContain('Grösser als');

    (el(fixture).querySelector('[data-testid="t.up.remove"] button') as HTMLElement).click();
    await fixture.whenStable();
    expect(fixture.componentInstance.uploads()).toEqual([]);
    http.verify();
  });
});

describe('MapViewComponent bubble', () => {
  it('emits bubble for fsp, basket, user and store markers only', () => {
    const fixture = TestBed.createComponent(MapViewComponent);
    const bubbles: string[] = [];
    const clicks: string[] = [];
    fixture.componentInstance.bubble.subscribe((marker) => bubbles.push(marker.kind));
    fixture.componentInstance.markerClick.subscribe((marker) => clicks.push(marker.kind));
    const kinds: MapMarker['kind'][] = ['fsp', 'basket', 'user', 'store', 'region', 'event', 'home'];
    for (const kind of kinds) {
      fixture.componentInstance.onMarkerClick({ id: kind, kind, lat: 47, lon: 8, label: kind });
    }
    expect(bubbles).toEqual(['fsp', 'basket', 'user', 'store']);
    expect(clicks).toEqual(kinds);
  });
});
