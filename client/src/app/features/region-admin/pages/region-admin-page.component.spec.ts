import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { RegionAdminItem, RegionImportStatus, RegionJoinMode, UnitType } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { RegionAdminPageComponent } from './region-admin-page.component';

const item = (slug: string, childCount: number): RegionAdminItem => ({
  region: { id: `id-${slug}`, slug, name: slug.toUpperCase(), type: UnitType.Country },
  childCount,
  memberCount: 10,
  ambassadors: [],
  functions: [],
  timeZone: 'Europe/Zurich',
  joinMode: RegionJoinMode.Open,
  moderated: false,
});

const page = (items: RegionAdminItem[]) => ({
  items,
  page: { page: 0, size: 25, totalItems: items.length, totalPages: 1 },
});

describe('RegionAdminPageComponent', () => {
  let fixture: ComponentFixture<RegionAdminPageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;
  const settle = () => fixture.whenStable();
  const component = () =>
    fixture.componentInstance as unknown as {
      onCsv(file: { name: string; size: number; text: string; rows: number }): void;
    };

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RegionAdminPageComponent);
    host = fixture.nativeElement as HTMLElement;
    await settle();
  });

  afterEach(() => http.verify());

  it('drills down the tree and offers creating children only below a parent', async () => {
    http.expectOne((req) => req.url === '/api/admin/regions' && !req.params.has('parentId')).flush(page([item('schweiz', 2)]));
    await settle();
    expect(host.querySelector('[data-testid="regionAdmin.create"]')).toBeNull();

    (host.querySelector('[data-testid="regionAdmin.open.schweiz"]') as HTMLButtonElement).click();
    await settle();
    http
      .expectOne((req) => req.url === '/api/admin/regions' && req.params.get('parentId') === 'id-schweiz')
      .flush(page([item('zuerich', 0)]));
    await settle();
    expect(host.querySelector('[data-testid="regionAdmin.create"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="regionAdmin.path"]')?.textContent).toContain('SCHWEIZ');
  });

  it('CSV import: dry run first, confirm only without errors', async () => {
    http.expectOne((req) => req.url === '/api/admin/regions').flush(page([]));
    await settle();
    (host.querySelector('[data-testid="regionAdmin.import"] button') as HTMLButtonElement).click();
    await settle();
    component().onCsv({ name: 'r.csv', size: 10, text: 'a;b', rows: 1 });
    const dry = http.expectOne('/api/admin/regions/import');
    expect(dry.request.body).toEqual({ csv: 'a;b', dryRun: true });
    dry.flush({
      rows: [{ line: 2, slug: 'b', status: RegionImportStatus.Error, errors: ['UNKNOWN_PARENT'] }],
      created: 0,
      updated: 0,
      errors: 1,
      dryRun: true,
    });
    await settle();
    expect(document.querySelector('[data-testid="regionAdmin.importSummary"]')).not.toBeNull();
    // the confirm button stays disabled while the preview has errors
    const confirm = document.querySelector('[data-testid="regionAdmin.importConfirm"] button') as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
  });
});
