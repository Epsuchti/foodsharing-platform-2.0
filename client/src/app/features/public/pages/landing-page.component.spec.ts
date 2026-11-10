import { HttpTestingController } from '@angular/common/http/testing';
import {
  ComponentFixture,
  DeferBlockBehavior,
  DeferBlockFixture,
  DeferBlockState,
  TestBed,
} from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { Explainer } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import {
  GOOGLE_JOIN_URL,
  LANDING_FAQ_GROUPS,
  LandingPageComponent,
} from './landing-page.component';

const EXPLAINER: Explainer = {
  counters: { foodsavers: 12480, regions: 14, kgSaved: 1842300, pickups: 96420, stores: 1930 },
  journeyPreview: [],
} as unknown as Explainer;

/** The sections of Variant D in their order (proposal §2). */
const SECTIONS = [
  'public.landing.hero',
  'public.landing.mission',
  'public.explainer.state',
  'public.explainer.journey',
  'public.landing.near',
  'public.landing.fairteiler',
  'public.explainer.business',
  'public.landing.moreWays',
  'public.landing.duties',
  'public.explainer.faq',
  'public.landing.band',
];

describe('LandingPageComponent (C17)', () => {
  let fixture: ComponentFixture<LandingPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideOptimus()],
      deferBlockBehavior: DeferBlockBehavior.Manual,
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LandingPageComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const byTestId = (id: string) => host.querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function renderNear(): Promise<DeferBlockFixture> {
    const [near] = await fixture.getDeferBlocks();
    await near.render(DeferBlockState.Complete);
    http.expectOne('/api/public/map/markers/regions').flush([]);
    http.expectOne('/api/public/map/markers/food-share-points').flush([]);
    await fixture.whenStable();
    return near;
  }

  it('renders the sections of Variant D in order', async () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    await fixture.whenStable();
    const elements = SECTIONS.map((id) => byTestId(id));
    elements.forEach((element, i) => expect(element, SECTIONS[i]).not.toBeNull());
    for (let i = 1; i < elements.length; i++) {
      // DOCUMENT_POSITION_FOLLOWING: the next section comes after the previous one
      expect(
        elements[i - 1]!.compareDocumentPosition(elements[i]!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
  });

  it('puts E-Mail first (solid) and Google second (outlined) in the hero and the band', () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    for (const [email, google] of [
      ['public.landing.joinEmail', 'public.landing.joinGoogle'],
      ['public.landing.band.joinEmail', 'public.landing.band.joinGoogle'],
    ]) {
      const e = byTestId(email)!;
      const g = byTestId(google)!;
      expect(e.compareDocumentPosition(g) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(e.classList).not.toContain('fs-btn--google');
      expect(g.classList).toContain('fs-btn--google');
      expect(g.getAttribute('href')).toBe(GOOGLE_JOIN_URL);
      expect(e.getAttribute('href')).toBe('/login?method=email');
    }
  });

  it('shows the four counters of the strip, without regions', async () => {
    expect(byTestId('public.explainer.state')!.getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    await fixture.whenStable();
    for (const key of ['kgSaved', 'pickups', 'foodsavers', 'stores']) {
      expect(byTestId(`public.explainer.counter.${key}`), key).not.toBeNull();
    }
    expect(byTestId('public.explainer.counter.pickups')!.getAttribute('data-value')).toBe('96420');
    expect(byTestId('public.explainer.counter.regions')).toBeNull();
  });

  it('has the static steps, FAQ groups, duties and partner link', () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    const preview = byTestId('public.explainer.preview')!;
    expect(preview.tagName).toBe('OL');
    expect(preview.querySelectorAll('button, [aria-current]').length).toBe(0);
    for (const n of [1, 2, 3, 4]) {
      expect(byTestId(`public.landing.journey.step.${n}`), `step ${n}`).not.toBeNull();
    }
    for (const group of LANDING_FAQ_GROUPS) {
      const element = byTestId(`public.explainer.faq.group.${group.id}`)!;
      expect(element.querySelectorAll('details').length).toBe(group.items.length);
    }
    expect(LANDING_FAQ_GROUPS.flatMap((g) => g.items).length).toBe(12);
    for (const id of [
      'reliable',
      'take',
      'nosale',
      'conduct',
      'privacy',
      'not.min',
      'not.money',
      'not.alone',
      'rules',
    ]) {
      expect(byTestId(`public.landing.duties.${id}`), id).not.toBeNull();
    }
    for (const id of ['free', 'cost', 'staff', 'accept', 'reliable', 'liability']) {
      expect(byTestId(`public.landing.business.benefit.${id}`), id).not.toBeNull();
    }
    expect(byTestId('public.landing.business.quote')).not.toBeNull();
    expect(byTestId('public.landing.business.cta')!.getAttribute('href')).toBe(
      '/support?from=%2F&subject=partner',
    );
    for (const id of ['member', 'home', 'business']) {
      expect(byTestId(`public.landing.way.${id}`), id).not.toBeNull();
    }
    for (const id of ['basket', 'donate', 'it']) {
      expect(byTestId(`public.landing.moreWays.${id}`), id).not.toBeNull();
    }
  });

  it('defers "In deiner Nähe" and embeds the MapView only after the inner defer (decision 176)', async () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    expect(byTestId('public.landing.near.search')).toBeNull();
    expect(host.querySelector('fs-landing-map')).toBeNull();
    const near = await renderNear();
    expect(byTestId('public.landing.near.search')).not.toBeNull();
    // the section is rendered, the map is not: a plain frame, no MapView, no tiles
    expect(byTestId('public.landing.near.map.placeholder')).not.toBeNull();
    expect(host.querySelector('fs-map-view, .leaflet-container')).toBeNull();
    expect(host.querySelector('img')).toBeNull();

    const [inner] = await near.getDeferBlocks();
    (globalThis as { __fsTileUrl?: string }).__fsTileUrl = '/stub/{z}/{x}/{y}.png';
    try {
      await inner.render(DeferBlockState.Complete);
      await fixture.whenStable();
      expect(host.querySelector('fs-map-view')).not.toBeNull();
      expect(byTestId('public.landing.near.map.placeholder')).toBeNull();
    } finally {
      delete (globalThis as { __fsTileUrl?: string }).__fsTileUrl;
    }
  });

  it('puts each "way" icon in a decorative tile (decision 175)', () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    for (const id of ['member', 'home', 'business']) {
      const tile = byTestId(`public.landing.way.${id}`)!.querySelector('.fs-tile');
      expect(tile, id).not.toBeNull();
      expect(tile!.getAttribute('aria-hidden'), id).toBe('true');
      expect(tile!.querySelector('.pi'), id).not.toBeNull();
    }
  });

  it('marks the bubbles as decorative', () => {
    http.expectOne('/api/public/onboarding/explainer').flush(EXPLAINER);
    const bubbles = host.querySelectorAll('fs-landing-bubble');
    expect(bubbles.length).toBe(5);
    bubbles.forEach((bubble) => expect(bubble.getAttribute('aria-hidden')).toBe('true'));
  });
});
