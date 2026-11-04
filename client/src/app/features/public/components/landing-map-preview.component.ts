import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { MapMarker } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { FRAME, LAND_SHAPES, framePercent, projectToFrame, shapePath } from '../landing-geo';
import { LandingMarkersResource } from '../landing-markers.resource';

interface PreviewPin {
  readonly id: string;
  readonly kind: 'region' | 'fsp';
  readonly left: number;
  readonly top: number;
}

/** The Fairteiler glyph of the map (`KIND_ICON.fsp` in map-view), copied so map-view stays untouched. */
const FSP_ICON = 'pi-box';

/**
 * Static, tile-free preview of the map in "In deiner Nähe" (C17, decision 160, map option 1): a stylised DACH
 * drawing with the public region and Fairteiler markers projected onto it, a note and "Karte öffnen" → `/map`.
 * No network request of its own and no third-party host; the pins come from the shared LandingMarkersResource and
 * appear only once it has loaded. On error the pins are left out silently: no DataState, no loading state, by
 * design (proposal §5).
 */
@Component({
  selector: 'fs-landing-map-preview',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="preview" data-testid="public.landing.near.preview">
      <div
        class="preview__canvas"
        role="img"
        [attr.aria-label]="'public.landing.near.previewAlt' | t"
      >
        <div class="preview__stage">
          <svg
            [attr.viewBox]="viewBox"
            preserveAspectRatio="xMidYMid meet"
            aria-hidden="true"
            focusable="false"
          >
            <g class="land">
              @for (d of land; track $index) {
                <path [attr.d]="d" />
              }
            </g>
            <g class="park">
              <circle cx="290" cy="380" r="7" />
              <circle cx="360" cy="300" r="12" />
              <circle cx="300" cy="200" r="10" />
              <circle cx="440" cy="380" r="9" />
              <circle cx="240" cy="160" r="8" />
            </g>
            <g class="river">
              <path d="M250 330 C262 300 300 260 330 200 S380 100 402 60" />
              <path d="M280 372 c6 -2 12 -1 16 2" />
              <path d="M420 370 c20 -4 40 -4 60 -8" />
            </g>
            <g class="lake">
              <ellipse cx="294" cy="371" rx="4" ry="1.6" transform="rotate(35 294 371)" />
              <ellipse cx="300" cy="320" rx="9" ry="3.5" />
              <ellipse cx="214" cy="410" rx="10" ry="3" />
            </g>
            <g class="label">
              <text x="300" y="150">DE</text>
              <text x="470" y="372">AT</text>
              <text x="240" y="380">CH</text>
            </g>
          </svg>
          <div class="preview__pins" aria-hidden="true">
            @for (pin of pins(); track pin.kind + pin.id) {
              <span
                class="pin pin--{{ pin.kind }}"
                [attr.data-pin]="pin.kind"
                [style.left.%]="pin.left"
                [style.top.%]="pin.top"
                ><span class="pin__glyph"
                  ><i class="pi {{ pin.kind === 'fsp' ? fspIcon : 'pi-users' }}"></i></span
              ></span>
            }
          </div>
        </div>
      </div>
      <div class="preview__over">
        <p data-testid="public.landing.near.previewNote">
          @if (counts(); as c) {
            {{ 'public.landing.near.previewNote' | t: c }}
          } @else {
            {{ 'public.landing.near.previewNoteNoCount' | t }}
          }
        </p>
        <a class="fs-btn" routerLink="/map" data-testid="public.landing.near.preview.openMap">
          <i class="pi pi-map-marker" aria-hidden="true"></i>
          <span>{{ 'public.landing.near.openMap' | t }}</span>
        </a>
      </div>
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    .preview {
      display: grid;
      overflow: hidden;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
    }
    .preview__canvas {
      position: relative;
      height: clamp(16rem, 36vw, 24rem);
      background: var(--fs-pub-map-water);
      container-type: size;
    }
    /* the stage keeps the frame's aspect ratio and fits the canvas (all of DACH in view), so pins stay on the land */
    .preview__stage {
      position: absolute;
      top: 50%;
      left: 50%;
      width: min(100cqw, calc(100cqh * 410 / 420));
      height: min(100cqh, calc(100cqw * 420 / 410));
      transform: translate(-50%, -50%);
    }
    svg {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
    }
    .land {
      fill: var(--fs-pub-map-land);
      stroke: var(--fs-pub-map-line);
      stroke-width: 1.5;
      stroke-linejoin: round;
    }
    .park {
      fill: var(--fs-pub-map-park);
    }
    .river {
      fill: none;
      stroke: var(--fs-pub-map-water);
      stroke-width: 3;
      stroke-linecap: round;
    }
    .lake {
      fill: var(--fs-pub-map-water);
    }
    .label {
      fill: var(--fs-text-muted);
      font-family: var(--fs-font);
      font-size: 11px;
      font-weight: 700;
      opacity: 0.55;
    }
    .preview__pins {
      position: absolute;
      inset: 0;
    }
    .pin {
      position: absolute;
      transform: translate(-50%, -100%);
    }
    .pin__glyph {
      display: grid;
      width: 1.6rem;
      height: 1.6rem;
      place-items: center;
      border: 2px solid var(--fs-surface);
      border-radius: 50% 50% 50% 0;
      box-shadow: var(--fs-shadow-md);
      transform: rotate(-45deg);
    }
    .pin__glyph .pi {
      font-size: 0.8rem;
      transform: rotate(45deg);
    }
    .pin--region .pin__glyph {
      background: var(--fs-primary);
      color: var(--fs-on-primary);
    }
    /* the only accent besides the warm bubble blob, and always with the fridge glyph (decision 160, O1) */
    .pin--fsp .pin__glyph {
      background: var(--fs-accent);
      color: #1a2a20;
    }
    .preview__over {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.6rem 1rem;
      padding: 0.8rem 1rem;
      border-top: 1px solid var(--fs-border);
    }
    .preview__over p {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class LandingMapPreviewComponent {
  private readonly markers = inject(LandingMarkersResource);

  protected readonly viewBox = `${FRAME.x} ${FRAME.y} ${FRAME.w} ${FRAME.h}`;
  protected readonly land = [LAND_SHAPES.de, LAND_SHAPES.at, LAND_SHAPES.ch].map(shapePath);
  protected readonly fspIcon = FSP_ICON;

  protected readonly pins = computed<PreviewPin[]>(() => {
    if (this.markers.state.status() !== 'success') {
      return [];
    }
    const set = this.markers.state.data();
    return [...toPins(set?.regions ?? [], 'region'), ...toPins(set?.fsps ?? [], 'fsp')];
  });

  /** `{ regions, fsps }` for the note once both lists are loaded; `null` otherwise. */
  protected readonly counts = computed(() => {
    const set = this.markers.state.status() === 'success' ? this.markers.state.data() : undefined;
    return set?.regions && set.fsps ? { regions: set.regions.length, fsps: set.fsps.length } : null;
  });
}

function toPins(markers: readonly MapMarker[], kind: PreviewPin['kind']): PreviewPin[] {
  const pins: PreviewPin[] = [];
  for (const marker of markers) {
    const point = projectToFrame(marker.lat, marker.lon);
    if (point) {
      pins.push({ id: marker.id, kind, ...framePercent(point) });
    }
  }
  return pins;
}
