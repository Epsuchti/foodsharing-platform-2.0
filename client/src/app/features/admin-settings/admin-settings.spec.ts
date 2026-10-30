import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { AdminUnitKind, ApiErrorCode, FeatureToggleKey, UnitType } from '../../api';
import { FeatureToggleService } from '../../core/feature-toggles/feature-toggle.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { configurationLabelKey, configurationType, formatBytes, isImage, toggleOrder } from './admin-settings.util';
import { AdminsPageComponent, adminsRequest, unitLink } from './pages/admins-page.component';
import { ConfigurationPageComponent } from './pages/configuration-page.component';
import { parseSlugs } from './pages/donations-page.component';
import { EmailBlocklistPageComponent } from './pages/email-blocklist-page.component';
import { FeatureTogglesPageComponent } from './pages/feature-toggles-page.component';
import { IpBlocksPageComponent } from './pages/ip-blocks-page.component';
import { UploadsPageComponent, uploadQuery } from './pages/uploads-page.component';

const EMPTY_PAGE = { page: 0, size: 25, totalItems: 0, totalPages: 0 };
const BOT = { id: 'u11', displayName: 'Bot Botschafter', role: 'AMBASSADOR', verified: true };

describe('admin settings helpers', () => {
  it('know the configuration types and label keys', () => {
    expect(configurationType('support.email')).toBe('EMAIL');
    expect(configurationType('petition.signatures')).toBe('INTEGER');
    expect(configurationType('donation.show-modal')).toBe('BOOLEAN');
    expect(configurationType('donation.campaign-page-slugs')).toBe('LIST');
    expect(configurationType('something.new')).toBe('STRING');
    expect(configurationLabelKey('donation.modal-hours-logged-in')).toBe('adminSettings.configuration.key.donation_modal_hours_logged_in');
  });

  it('format sizes, detect images, order toggles', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(734_003_200)).toBe('700 MB');
    expect(isImage('image/png')).toBe(true);
    expect(isImage('application/pdf')).toBe(false);
    expect(toggleOrder(FeatureToggleKey.Baskets)).toBeLessThan(toggleOrder(FeatureToggleKey.GlobalSearch));
    expect(toggleOrder('UNKNOWN')).toBe(Number.MAX_SAFE_INTEGER);
  });

  it('build the list queries and links', () => {
    expect(uploadQuery('ALL', 0)).toEqual({ page: 0, size: 25 });
    expect(uploadQuery('UNCLAIMED', 1)).toEqual({ unclaimed: true, page: 1, size: 25 });
    expect(uploadQuery('USER_PHOTO', 0)).toEqual({ module: 'USER_PHOTO', page: 0, size: 25 });
    expect(adminsRequest({ q: '  ', kind: 'ALL', active: 'ALL', page: 0 })).toEqual({ q: undefined, unitKind: undefined, active: undefined, page: 0, size: 25 });
    expect(adminsRequest({ q: ' bot ', kind: AdminUnitKind.WorkingGroup, active: 'INACTIVE', page: 2 })).toEqual({
      q: 'bot',
      unitKind: AdminUnitKind.WorkingGroup,
      active: false,
      page: 2,
      size: 25,
    });
    expect(unitLink({ id: 'g1', slug: 'g', name: 'G', type: UnitType.WorkingGroup })).toEqual(['/groups', 'g1']);
    expect(unitLink({ id: 'r1', slug: 'r', name: 'R', type: UnitType.City })).toEqual(['/regions', 'r1']);
    expect(parseSlugs(' a, ,b ,c')).toEqual(['a', 'b', 'c']);
  });
});

describe('admin settings pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const qa = (selector: string) => harness.routeNativeElement!.querySelectorAll(selector);
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const settle = () => harness.fixture.whenStable();
  const click = (selector: string) => (q(`${selector} button`) as HTMLButtonElement).click();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'toggles', component: FeatureTogglesPageComponent },
            { path: 'configuration', component: ConfigurationPageComponent },
            { path: 'blocklist', component: EmailBlocklistPageComponent },
            { path: 'ip-blocks', component: IpBlocksPageComponent },
            { path: 'uploads', component: UploadsPageComponent },
            { path: 'admins', component: AdminsPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('feature toggles: loading, enum order, disabling asks first and updates the toggle service', async () => {
    const toggles = TestBed.inject(FeatureToggleService);
    const load = toggles.load();
    http.expectOne('/api/public/feature-toggles').flush({ BASKETS: true, BLOG: true });
    await load;

    await harness.navigateByUrl('/toggles');
    expect(state('adminSettings.toggles.state')).toBe('loading');
    http.expectOne('/api/admin/feature-toggles').flush([
      { key: 'BLOG', enabled: true, defaultEnabled: true },
      { key: 'BASKETS', enabled: true, defaultEnabled: true },
    ]);
    await settle();
    const rows = [...qa('[data-testid="adminSettings.toggles.row"]')].map((row) => row.getAttribute('data-key'));
    expect(rows).toEqual(['BASKETS', 'BLOG']);

    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    click('[data-key="BASKETS"] [data-testid="adminSettings.toggles.switch"]');
    expect(http.match('/api/admin/feature-toggles/BASKETS').length).toBe(0);
    confirm.mock.calls[0][0].accept!();
    const patch = http.expectOne('/api/admin/feature-toggles/BASKETS');
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ enabled: false });
    patch.flush({ key: 'BASKETS', enabled: false, defaultEnabled: true });
    await settle();
    expect(q('[data-key="BASKETS"]')?.getAttribute('data-enabled')).toBe('false');
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(false);

    // switching on does not ask
    click('[data-key="BASKETS"] [data-testid="adminSettings.toggles.switch"]');
    http.expectOne('/api/admin/feature-toggles/BASKETS').flush({ key: 'BASKETS', enabled: true, defaultEnabled: true });
    await settle();
    expect(confirm).toHaveBeenCalledTimes(1);
    expect(toggles.isEnabled(FeatureToggleKey.Baskets)).toBe(true);
  });

  it('configuration: typed inputs, invalid value shown at the row, saved value replaces the row', async () => {
    await harness.navigateByUrl('/configuration');
    http.expectOne('/api/admin/configuration').flush([
      { key: 'support.email', value: 'support@foodsharing.network', defaultValue: 'support@foodsharing.network' },
      { key: 'petition.signatures', value: '0', defaultValue: '0' },
      { key: 'donation.show-modal', value: 'false', defaultValue: 'false' },
    ]);
    await settle();
    expect(q('[data-key="support.email"] input')?.getAttribute('type')).toBe('email');
    expect(q('[data-key="petition.signatures"] input')?.getAttribute('type')).toBe('number');
    expect(q('[data-key="donation.show-modal"]')?.getAttribute('data-type')).toBe('BOOLEAN');

    const input = q('[data-key="support.email"] input') as HTMLInputElement;
    input.value = 'not-an-address';
    input.dispatchEvent(new Event('input'));
    await settle();
    click('[data-key="support.email"] [data-testid="adminSettings.configuration.save"]');
    const bad = http.expectOne('/api/admin/configuration/support.email');
    expect(bad.request.body).toEqual({ value: 'not-an-address' });
    bad.flush(apiHttpError(400, ApiErrorCode.ConfigurationValueInvalid).error, { status: 400, statusText: 'Bad Request' });
    await settle();
    expect(q('[data-key="support.email"] [data-testid="adminSettings.configuration.error"]')).not.toBeNull();

    input.value = 'help@example.org';
    input.dispatchEvent(new Event('input'));
    await settle();
    expect(q('[data-testid="adminSettings.configuration.error"]')).toBeNull();
    click('[data-key="support.email"] [data-testid="adminSettings.configuration.save"]');
    http
      .expectOne('/api/admin/configuration/support.email')
      .flush({ key: 'support.email', value: 'help@example.org', defaultValue: 'support@foodsharing.network', updatedAt: '2026-11-02T10:00:00Z' });
    await settle();
    expect((q('[data-key="support.email"] input') as HTMLInputElement).value).toBe('help@example.org');
    expect((q('[data-key="support.email"] [data-testid="adminSettings.configuration.save"] button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('configuration: history drawer loads SYSTEM configuration changes', async () => {
    await harness.navigateByUrl('/configuration');
    http.expectOne('/api/admin/configuration').flush([]);
    await settle();
    expect(state('adminSettings.configuration.state')).toBe('empty');
    click('[data-testid="adminSettings.configuration.historyButton"]');
    await settle();
    const history = http.expectOne((req) => req.url === '/api/history');
    expect(history.request.params.get('subjectType')).toBe('SYSTEM');
    expect(history.request.params.get('subjectId')).toBe('00000000-0000-0000-0000-000000000000');
    expect(history.request.params.getAll('actions')).toEqual(['ADMIN_CONFIGURATION_CHANGED']);
    history.flush({ items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } });
  });

  it('blocklist: duplicate shown at the field, entry added on success', async () => {
    await harness.navigateByUrl('/blocklist');
    http.expectOne('/api/admin/email-blocklist').flush([]);
    await settle();
    expect(state('adminSettings.blocklist.state')).toBe('empty');
    const pattern = q('[data-testid="adminSettings.blocklist.pattern"]') as HTMLInputElement;
    pattern.value = 'spam.example';
    pattern.dispatchEvent(new Event('input'));
    await settle();
    click('[data-testid="adminSettings.blocklist.addButton"]');
    http
      .expectOne((req) => req.method === 'POST' && req.url === '/api/admin/email-blocklist')
      .flush(apiHttpError(409, ApiErrorCode.BlocklistDuplicate).error, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(q('[data-testid="adminSettings.blocklist.error"]')).not.toBeNull();
    click('[data-testid="adminSettings.blocklist.addButton"]');
    http
      .expectOne((req) => req.method === 'POST' && req.url === '/api/admin/email-blocklist')
      .flush({ id: 'b1', pattern: 'spam.example', createdAt: '2026-11-02T10:00:00Z' });
    await settle();
    expect(qa('[data-testid="adminSettings.blocklist.row"]').length).toBe(1);
    expect(q('[data-testid="adminSettings.blocklist.error"]')).toBeNull();
  });

  it('ip blocks: error state with retry', async () => {
    await harness.navigateByUrl('/ip-blocks');
    http.expectOne('/api/admin/ip-blocks').flush(apiHttpError(503, ApiErrorCode.UnexpectedError).error, { status: 503, statusText: 'Error' });
    await settle();
    expect(state('adminSettings.ipBlocks.state')).toBe('error');
  });

  it('uploads: summary, unclaimed filter resets the page and deletes after confirm', async () => {
    await harness.navigateByUrl('/uploads');
    http.expectOne('/api/admin/uploads/summary').flush({ count: 2, bytes: 2048, unclaimedCount: 1, byModule: [{ module: 'USER_PHOTO', count: 1, bytes: 1024 }] });
    http.expectOne((req) => req.url === '/api/admin/uploads').flush({ items: [], page: EMPTY_PAGE });
    await settle();
    expect(q('[data-testid="adminSettings.uploads.count"]')?.textContent?.trim()).toBe('2');
    expect(state('adminSettings.uploads.state')).toBe('empty');

    (harness.routeDebugElement!.componentInstance as { setFilter(v: string): void }).setFilter('UNCLAIMED');
    await settle();
    const filtered = http.expectOne((req) => req.url === '/api/admin/uploads');
    expect(filtered.request.params.get('unclaimed')).toBe('true');
    expect(filtered.request.params.get('module')).toBeNull();
    filtered.flush({
      items: [{ id: 'up1', fileName: 'flyer.png', mimeType: 'image/png', size: 1024, createdAt: '2026-11-02T08:00:00Z', expiresAt: '2026-11-03T08:00:00Z' }],
      page: { ...EMPTY_PAGE, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(q('[data-testid="adminSettings.uploads.row"] img')?.getAttribute('src')).toBe('/api/uploads/up1?size=sm');
    const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
    click('[data-testid="adminSettings.uploads.delete"]');
    confirm.mock.calls[0][0].accept!();
    http.expectOne((req) => req.method === 'DELETE' && req.url === '/api/admin/uploads/up1').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne((req) => req.url === '/api/admin/uploads').flush({ items: [], page: EMPTY_PAGE });
    http.expectOne('/api/admin/uploads/summary').flush({ count: 1, bytes: 1024, unclaimedCount: 0, byModule: [] });
  });

  it('admins: filters go to the request, disabling needs a reason and reloads', async () => {
    await harness.navigateByUrl('/admins');
    http.expectOne((req) => req.url === '/api/admin/admins').flush({
      items: [{ user: BOT, email: 'bot@local', active: true, lastLoginAt: '2026-11-01T08:00:00Z', units: [{ region: { id: 'r40', slug: 'zh', name: 'Zürich', type: 'CITY' } }] }],
      page: { ...EMPTY_PAGE, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(qa('[data-testid="admin.admins.row"]').length).toBe(1);
    expect(q('[data-testid="admin.admins.unit"]')?.getAttribute('href')).toBe('/regions/r40');

    const page = harness.routeDebugElement!.componentInstance as { setKind(v: string): void; setActive(v: string): void };
    page.setKind('WORKING_GROUP');
    page.setActive('INACTIVE');
    await settle();
    const filtered = http.expectOne((req) => req.url === '/api/admin/admins');
    expect(filtered.request.params.get('unitKind')).toBe('WORKING_GROUP');
    expect(filtered.request.params.get('active')).toBe('false');
    filtered.flush({
      items: [{ user: BOT, email: 'bot@local', active: true, units: [] }],
      page: { ...EMPTY_PAGE, totalItems: 1, totalPages: 1 },
    });
    await settle();

    click('[data-testid="admin.admins.toggleActive"]');
    await settle();
    const reason = document.querySelector('[data-testid="admin.admins.activeDialog.message"]') as HTMLTextAreaElement;
    reason.value = 'Left the team';
    reason.dispatchEvent(new Event('input'));
    await settle();
    (document.querySelector('[data-testid="admin.admins.activeDialog.confirm"] button') as HTMLButtonElement).click();
    const put = http.expectOne('/api/admin/users/u11/active');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ active: false, reason: 'Left the team' });
    put.flush({ id: 'u11', displayName: 'Bot Botschafter', active: false });
    await settle();
    http.expectOne((req) => req.url === '/api/admin/admins').flush({ items: [], page: EMPTY_PAGE });
  });
});
