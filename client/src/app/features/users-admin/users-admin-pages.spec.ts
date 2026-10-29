import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, Language, Role, Theme, UserNote, UserProfile } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo } from '../auth/testing';
import { BouncedUsersPageComponent } from './pages/bounced-users-page.component';
import { RegionUsersPageComponent } from './pages/region-users-page.component';
import { UserAdminPageComponent } from './pages/user-admin-page.component';

const REGION_ID = '00000000-0000-4000-8000-000000000101';
const USER_ID = '00000001-0001-4000-8000-000000000006';

const PROFILE: UserProfile = {
  id: USER_ID,
  email: 'foodsaver@local',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
  active: true,
};

describe('users admin pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const q = (selector: string) => harness.routeNativeElement!.querySelector(selector);
  const settle = () => harness.fixture.whenStable();

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          routes: [
            { path: 'regions/:regionId/users', component: RegionUsersPageComponent },
            { path: 'users/:userId', component: UserAdminPageComponent },
            { path: 'admin/bounced-emails', component: BouncedUsersPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('user list: filters go to the members endpoint and reset the page', async () => {
    await harness.navigateByUrl(`/regions/${REGION_ID}/users`);
    http
      .expectOne(`/api/regions/${REGION_ID}`)
      .flush({ id: REGION_ID, name: 'Zürich', membership: { member: true, admin: true, homeRegion: true } });
    await settle();
    const first = http.expectOne((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(first.request.params.get('verified')).toBeNull();
    first.flush({
      items: [{ user: { id: USER_ID, displayName: 'Sarah', role: Role.Foodsaver, verified: false }, admin: false, verified: false, permissions: [] }],
      page: { page: 0, size: 25, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(q('[data-testid="usersAdmin.list.row"] a')?.getAttribute('href')).toBe(`/users/${USER_ID}`);

    const page = harness.routeDebugElement!.componentInstance as {
      setFilter<T>(filter: { set(value: T): void }, value: T): void;
      verified: { set(value: string): void };
    };
    page.setFilter(page.verified, 'no');
    await settle();
    const filtered = http.expectOne((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(filtered.request.params.get('verified')).toBe('false');
    expect(filtered.request.params.get('page')).toBe('0');
    filtered.flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
  });

  it('user list: members without user management get the forbidden state, no member request', async () => {
    await harness.navigateByUrl(`/regions/${REGION_ID}/users`);
    http
      .expectOne(`/api/regions/${REGION_ID}`)
      .flush({ id: REGION_ID, name: 'Zürich', membership: { member: true, admin: false, homeRegion: true } });
    await settle();
    http.expectNone((req) => req.url === `/api/regions/${REGION_ID}/members`);
    expect(q('[data-testid="usersAdmin.list.state"]')?.getAttribute('data-state')).toBe('forbidden');
  });

  /** Opens the user page and answers profile, internal notes and history. */
  async function openUser(profile: UserProfile = PROFILE, notes: UserNote[] = []): Promise<void> {
    await harness.navigateByUrl(`/users/${USER_ID}`);
    http.expectOne(`/api/admin/users/${USER_ID}/profile`).flush(profile);
    http.expectOne(`/api/admin/users/${USER_ID}/notes`).flush(notes);
    await settle();
    http.match((req) => req.url.endsWith('/change-history') || req.url.includes('/history/actions')).forEach((req) =>
      req.flush(req.request.url.endsWith('/change-history') ? { items: [], page: { page: 0, size: 20, totalItems: 0, totalPages: 0 } } : []),
    );
    await settle();
  }

  it('user page: disabling needs a reason and updates the status', async () => {
    await openUser();

    (q('[data-testid="usersAdmin.user.toggleActive"] button') as HTMLButtonElement).click();
    await settle();
    const confirm = document.querySelector('[data-testid="usersAdmin.user.activeDialog.confirm"] button') as HTMLButtonElement;
    confirm.click();
    await settle();
    http.expectNone(`/api/admin/users/${USER_ID}/active`);

    const textarea = document.querySelector('[data-testid="usersAdmin.user.activeDialog.message"]') as HTMLTextAreaElement;
    textarea.value = 'Mehrfach nicht erschienen';
    textarea.dispatchEvent(new Event('input'));
    await settle();
    confirm.click();
    const request = http.expectOne(`/api/admin/users/${USER_ID}/active`);
    expect(request.request.body).toEqual({ active: false, reason: 'Mehrfach nicht erschienen' });
    request.flush({ ...PROFILE, active: false });
    await settle();
    expect(q('[data-testid="usersAdmin.user.inactive"]')).not.toBeNull();
  });

  it('user page (Q4): bounce flag is cleared, protection toggled, email change errors stay in the dialog', async () => {
    await openUser({ ...PROFILE, emailBouncedAt: '2026-11-01T10:00:00Z', noAutomaticDelete: false });
    expect(q('[data-testid="usersAdmin.user.bouncedTag"]')).not.toBeNull();
    expect(q('[data-testid="usersAdmin.user.bounce"]')?.getAttribute('data-bounced')).toBe('true');
    // only ORGA may delete
    expect(q('[data-testid="usersAdmin.user.delete"]')).toBeNull();

    (q('[data-testid="usersAdmin.user.clearBounce"] button') as HTMLButtonElement).click();
    const clear = http.expectOne(`/api/users/${USER_ID}/email-bounce`);
    expect(clear.request.method).toBe('DELETE');
    clear.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    expect(q('[data-testid="usersAdmin.user.bouncedTag"]')).toBeNull();

    (q('[data-testid="usersAdmin.user.noAutomaticDelete"] input') as HTMLInputElement).click();
    await settle();
    const protect = http.expectOne(`/api/admin/users/${USER_ID}/no-automatic-delete`);
    expect(protect.request.method).toBe('PATCH');
    expect(protect.request.body).toEqual({ value: true });
    protect.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http.expectOne(`/api/admin/users/${USER_ID}/profile`).flush({ ...PROFILE, noAutomaticDelete: true });
    await settle();
    expect((q('[data-testid="usersAdmin.user.noAutomaticDelete"] input') as HTMLInputElement).checked).toBe(true);

    (q('[data-testid="usersAdmin.user.changeEmail"] button') as HTMLButtonElement).click();
    await settle();
    const input = document.querySelector('[data-testid="usersAdmin.user.newEmail"]') as HTMLInputElement;
    input.value = 'sarah.neu@local';
    input.dispatchEvent(new Event('input'));
    await settle();
    (document.querySelector('[data-testid="usersAdmin.user.sendEmailChange"] button') as HTMLButtonElement).click();
    const change = http.expectOne(`/api/admin/users/${USER_ID}/email`);
    expect(change.request.body).toEqual({ newEmail: 'sarah.neu@local' });
    change.flush(apiHttpError(409, ApiErrorCode.EmailTaken).error, { status: 409, statusText: 'Conflict' });
    await settle();
    expect(document.querySelector('[data-testid="usersAdmin.user.emailError"]')?.getAttribute('data-code')).toBe('EMAIL_TAKEN');
  });

  it('user page (Q4): ORGA deletes with a reason; a deleted account shows the archive flag', async () => {
    TestBed.inject(SessionService).applySession(sessionInfo({ role: Role.Orga, id: '00000001-0001-4000-8000-000000000014' }));
    await openUser();
    (q('[data-testid="usersAdmin.user.delete"] button') as HTMLButtonElement).click();
    await settle();
    const textarea = document.querySelector('[data-testid="usersAdmin.user.deleteDialog.message"]') as HTMLTextAreaElement;
    textarea.value = 'Wunsch per Mail';
    textarea.dispatchEvent(new Event('input'));
    await settle();
    (document.querySelector('[data-testid="usersAdmin.user.deleteDialog.confirm"] button') as HTMLButtonElement).click();
    const remove = http.expectOne(`/api/admin/users/${USER_ID}`);
    expect(remove.request.method).toBe('DELETE');
    expect(remove.request.body).toEqual({ reason: 'Wunsch per Mail' });
    remove.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http
      .expectOne(`/api/admin/users/${USER_ID}/profile`)
      .flush({ ...PROFILE, displayName: 'Gelöschtes Mitglied', active: false, deletedAt: '2026-11-02T09:00:00Z', deleteReason: 'Wunsch per Mail' });
    await settle();
    expect(q('[data-testid="usersAdmin.user.deletedTag"]')).not.toBeNull();
    expect(q('[data-testid="usersAdmin.user.deleteReason"]')?.textContent).toContain('usersAdmin.user.archiveReason');
    expect(q('[data-testid="usersAdmin.user.delete"]')).toBeNull();
    // no new notes on an archived account
    expect(q('[data-testid="usersAdmin.user.notes.composer"]')).toBeNull();
  });

  it('user page (A7): notes are listed and added on the wall', async () => {
    const note: UserNote = {
      id: 'note-1',
      body: 'Seit Monaten inaktiv',
      createdAt: '2026-05-02T09:00:00Z',
      mayDelete: true,
      author: { id: 'u11', displayName: 'Reto', role: Role.Ambassador, verified: true },
    };
    await openUser(PROFILE, [note]);
    expect(harness.routeNativeElement!.querySelectorAll('[data-testid="usersAdmin.user.notes.post"]').length).toBe(1);
    const text = q('[data-testid="usersAdmin.user.notes.text"]') as HTMLTextAreaElement;
    text.value = 'Telefonisch erreicht';
    text.dispatchEvent(new Event('input'));
    await settle();
    (q('[data-testid="usersAdmin.user.notes.submit"] button') as HTMLButtonElement).click();
    const add = http.expectOne((req) => req.method === 'POST' && req.url === `/api/admin/users/${USER_ID}/notes`);
    expect(add.request.body).toEqual({ body: 'Telefonisch erreicht' });
    add.flush({ ...note, id: 'note-2', body: 'Telefonisch erreicht' });
    await settle();
    http.expectOne(`/api/admin/users/${USER_ID}/notes`).flush([note, { ...note, id: 'note-2', body: 'Telefonisch erreicht' }]);
    await settle();
    expect(harness.routeNativeElement!.querySelectorAll('[data-testid="usersAdmin.user.notes.post"]').length).toBe(2);
  });

  it('bounced list: rows link to the user, clear reloads; empty and error states', async () => {
    await harness.navigateByUrl('/admin/bounced-emails');
    expect(q('[data-testid="usersAdmin.bounced.state"]')?.getAttribute('data-state')).toBe('loading');
    const first = http.expectOne((req) => req.url === '/api/admin/users/bounced');
    expect(first.request.params.get('regionId')).toBeNull();
    expect(first.request.params.get('size')).toBe('25');
    first.flush({
      items: [{ user: { id: USER_ID, displayName: 'Sarah', role: Role.Foodsaver, verified: true }, bouncedAt: '2026-11-01T10:00:00Z' }],
      page: { page: 0, size: 25, totalItems: 1, totalPages: 1 },
    });
    await settle();
    expect(q('[data-testid="usersAdmin.bounced.open"]')?.getAttribute('href')).toBe(`/users/${USER_ID}`);
    (q('[data-testid="usersAdmin.bounced.clear"] button') as HTMLButtonElement).click();
    http.expectOne(`/api/users/${USER_ID}/email-bounce`).flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    http
      .expectOne((req) => req.url === '/api/admin/users/bounced')
      .flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
    await settle();
    expect(q('[data-testid="usersAdmin.bounced.state"]')?.getAttribute('data-state')).toBe('empty');

    const page = harness.routeDebugElement!.componentInstance as { list: { reload(): void } };
    page.list.reload();
    http
      .expectOne((req) => req.url === '/api/admin/users/bounced')
      .flush(apiHttpError(403, ApiErrorCode.AccessDenied).error, { status: 403, statusText: 'Forbidden' });
    await settle();
    expect(q('[data-testid="usersAdmin.bounced.state"]')?.getAttribute('data-state')).toBe('forbidden');
  });
});
