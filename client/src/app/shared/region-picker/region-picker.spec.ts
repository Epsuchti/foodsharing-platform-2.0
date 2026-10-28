import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { PublicRegionNode, RegionMatch, RegionRef, UnitType } from '../../api';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPickerComponent, filterTree } from './region-picker.component';

const ZH: RegionRef = { id: 'zh', slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity };

const TREE: PublicRegionNode[] = [
  {
    id: 'ch',
    slug: 'schweiz',
    name: 'Schweiz',
    type: UnitType.Country,
    active: false,
    children: [
      { id: 'zh', slug: 'zuerich-stadt', name: 'Zürich Stadt', type: UnitType.BigCity, active: true, children: [] },
      { id: 'wt', slug: 'winterthur', name: 'Winterthur', type: UnitType.City, active: true, children: [] },
    ],
  },
];

describe('filterTree', () => {
  it('keeps matches and their ancestors', () => {
    expect(filterTree(TREE, 'winter')[0].children.map((node) => node.slug)).toEqual(['winterthur']);
    expect(filterTree(TREE, 'nothing')).toEqual([]);
    expect(filterTree(TREE, '')).toHaveLength(1);
  });
});

describe('RegionPickerComponent', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function create(mode: 'postcode' | 'tree' = 'postcode') {
    const fixture = TestBed.createComponent(RegionPickerComponent);
    fixture.componentRef.setInput('initialMode', mode);
    fixture.componentRef.setInput('testId', 't.picker');
    const picked: RegionRef[] = [];
    fixture.componentInstance.regionSelected.subscribe((region) => picked.push(region));
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const by = (id: string) => el.querySelector(`[data-testid="${id}"]`) as HTMLElement;
    return { fixture, el, by, picked };
  }

  async function typePostcode(fixture: { whenStable(): Promise<unknown> }, input: HTMLInputElement, value: string) {
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  it('suggests regions for a postcode and emits the chosen one', async () => {
    const { fixture, by, picked } = await create();
    await typePostcode(fixture, by('t.picker.postcode') as HTMLInputElement, '80');
    http.expectNone((r) => r.url.endsWith('/suggest'));

    await typePostcode(fixture, by('t.picker.postcode') as HTMLInputElement, '8004');
    const request = http.expectOne((r) => r.url.endsWith('/api/public/regions/suggest'));
    expect(request.request.params.get('postcode')).toBe('8004');
    expect(request.request.params.get('country')).toBe('CH');
    request.flush([{ region: ZH, matchedBy: RegionMatch.Postcode }]);
    await fixture.whenStable();

    by('t.picker.suggestion.zuerich-stadt').click();
    expect(picked).toEqual([ZH]);
  });

  it('shows a load error compactly in place and recovers on retry', async () => {
    const { fixture, by } = await create();
    await typePostcode(fixture, by('t.picker.postcode') as HTMLInputElement, '8004');
    http
      .expectOne((r) => r.url.endsWith('/suggest'))
      .flush({ code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true }, { status: 500, statusText: 'x' });
    await fixture.whenStable();
    const state = by('t.picker.suggestions');
    expect(state.getAttribute('data-state')).toBe('error');
    expect(state.classList).toContain('fs-data-state--compact');

    (state.querySelector('[data-testid="t.picker.suggestions.retry"] button') as HTMLElement).click();
    http.expectOne((r) => r.url.endsWith('/suggest')).flush([]);
    await fixture.whenStable();
    expect(by('t.picker.suggestions').getAttribute('data-state')).toBe('empty');
  });

  it('browses the public tree and filters it', async () => {
    const { fixture, by, picked } = await create('tree');
    http.expectOne((r) => r.url.endsWith('/api/public/regions/tree')).flush(TREE);
    await fixture.whenStable();
    const search = by('t.picker.search') as HTMLInputElement;
    search.value = 'zür';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(by('t.picker.node.winterthur')).toBeNull();
    by('t.picker.node.zuerich-stadt').click();
    expect(picked.map((region) => region.slug)).toEqual(['zuerich-stadt']);
  });
});
