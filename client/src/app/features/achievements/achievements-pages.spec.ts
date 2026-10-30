import { HttpTestingController } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import {
  Achievement,
  AchievementSystemKey,
  AchievementVisibility,
  ApiErrorCode,
  Award,
  DuplicateMode,
  Language,
  Role,
  SessionInfo,
  Theme,
} from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { RegionPageContext } from '../regions/region-page.context';
import { AdminAchievementsPageComponent } from './pages/admin-achievements-page.component';
import { RegionAchievementsTab } from './tabs/region-achievements.tab';

const BOT = '00000001-0001-4000-8000-000000000011';
const GUIDE = '00000001-0001-4000-8000-000000000010';
const ZH = '00000010-0001-4000-8000-000000000040';

function session(language: Language): SessionInfo {
  return {
    authenticated: true,
    permissions: [],
    user: { id: BOT, email: 'bot@local', displayName: 'Reto', role: Role.Ambassador, language, theme: Theme.System, verified: true, homeRegionId: ZH },
  };
}

function achievement(overrides: Partial<Achievement> = {}): Achievement {
  return {
    id: 'a3',
    regionId: ZH,
    name: 'Fairteiler-Putzheld',
    description: 'Hat beim Putztag mitgeholfen.',
    icon: 'sparkles',
    visibility: AchievementVisibility.Scope,
    duplicateMode: DuplicateMode.Multiple,
    awardedCount: 1,
    mayAdminister: false,
    ...overrides,
  };
}

const guide = { id: GUIDE, displayName: 'Julien Favre', role: Role.Foodsaver, verified: true };

function award(overrides: Partial<Award> = {}): Award {
  return {
    id: 'w1',
    achievementId: 'a3',
    achievementName: 'Fairteiler-Putzheld',
    icon: 'sparkles',
    user: guide,
    awardedBy: { id: BOT, displayName: 'Reto Baumann', role: Role.Ambassador, verified: true },
    awardedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  };
}

describe('achievement pages', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  const admin = signal(false);
  const q = (selector: string) => document.querySelector(selector);
  const all = (selector: string) => document.querySelectorAll(selector);
  const settle = () => harness.fixture.whenStable();
  const tick = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await settle();
  };
  const state = (testId: string) => q(`[data-testid="${testId}"]`)?.getAttribute('data-state');
  const click = (selector: string) => {
    const host = q(selector) as HTMLElement;
    ((host.querySelector('button') ?? host) as HTMLElement).click();
  };
  const type = (selector: string, value: string) => {
    const input = q(selector) as HTMLInputElement | HTMLTextAreaElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const retry = (testId: string) => (q(`[data-testid="${testId}"] [data-testid$=".retry"] button`) as HTMLButtonElement).click();
  const serverError = { status: 500, statusText: 'Server Error' };
  const regionList = (req: { url: string; params: { get(name: string): string | null } }) =>
    req.url === '/api/achievements' && req.params.get('regionId') === ZH;
  const globalList = (req: { url: string; params: { has(name: string): boolean } }) =>
    req.url === '/api/achievements' && !req.params.has('regionId');

  beforeEach(async () => {
    admin.set(false);
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({
          translations: {
            'achievements.card.awarded': '{count} awards',
            'achievements.visibility.SCOPE': 'Members',
            'achievements.visibility.HIDDEN': 'Hidden',
            'achievements.form.translationNameMissing': 'Name missing: {languages}',
          },
          routes: [
            {
              path: 'regions/:regionId/achievements',
              component: RegionAchievementsTab,
              providers: [{ provide: RegionPageContext, useValue: { regionId: signal(ZH), isAdmin: admin } }],
            },
            { path: 'admin/achievements', component: AdminAchievementsPageComponent },
          ],
        }),
        provideOptimus(),
      ],
    });
    TestBed.inject(SessionService).applySession(session(Language.De));
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  describe('region tab as member', () => {
    it('lists the region achievements read-only', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      expect(state('achievements.board.state')).toBe('loading');
      http.expectOne(regionList).flush([achievement(), achievement({ id: 'a4', name: 'Sommerfest-Helfer', validityDays: 180, awardedCount: 0 })]);
      await settle();
      expect(state('achievements.board.state')).toBe('success');
      expect(all('[data-testid="achievements.card"]').length).toBe(2);
      expect(q('[data-achievement-id="a3"] [data-testid="achievements.card.name"]')?.textContent).toContain('Fairteiler-Putzheld');
      expect(q('[data-achievement-id="a3"] [data-testid="achievements.card.count"]')?.textContent).toContain('1 awards');
      expect(q('[data-achievement-id="a3"] i')?.className).toContain('pi-sparkles');
      expect(q('[data-testid="achievements.board.define"]')).toBeNull();
      expect(q('[data-testid="achievements.card.award"]')).toBeNull();
      expect(q('[data-testid="achievements.card.edit"]')).toBeNull();
    });

    it('shows the empty state', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([]);
      await settle();
      expect(state('achievements.board.state')).toBe('empty');
    });

    it('shows the error state and retries', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle();
      expect(state('achievements.board.state')).toBe('error');
      retry('achievements.board.state');
      await settle();
      http.expectOne(regionList).flush([achievement()]);
      await settle();
      expect(state('achievements.board.state')).toBe('success');
    });

    it('shows the forbidden state for non-members', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush(apiHttpError(403, ApiErrorCode.AccessDenied).error, { status: 403, statusText: 'Forbidden' });
      await settle();
      http.match('/api/auth/session').forEach((request) => request.flush(session(Language.De)));
      await settle();
      expect(state('achievements.board.state')).toBe('forbidden');
    });
  });

  describe('region tab as ambassador', () => {
    beforeEach(() => admin.set(true));

    it('defines an achievement with translations and icon', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([]);
      await settle();
      click('[data-testid="achievements.board.define"]');
      await tick();
      const submit = () => q('[data-testid="achievements.form.submit"] button') as HTMLButtonElement;
      expect(submit().disabled).toBe(true);

      type('[data-testid="achievements.form.names.field.de"]', 'Velokurier');
      click('[data-testid="achievements.form.names.tab.en"]');
      await settle();
      type('[data-testid="achievements.form.names.field.en"]', 'Bike courier');
      // a translated description without its name blocks saving
      click('[data-testid="achievements.form.descriptions.tab.fr"]');
      await settle();
      type('[data-testid="achievements.form.descriptions.field.fr"]', 'Livre à vélo.');
      await settle();
      expect(q('[data-testid="achievements.form.translationNameMissing"]')?.textContent).toContain('FR');
      expect(submit().disabled).toBe(true);
      type('[data-testid="achievements.form.descriptions.field.fr"]', '');
      (q('[data-testid="achievements.form.icon"][data-icon="truck"] input') as HTMLInputElement).click();
      type('[data-testid="achievements.form.validity"]', '30');
      await settle();
      expect(q('[data-testid="achievements.form.icon"][data-icon="truck"]')?.classList).toContain('icon--active');
      expect(submit().disabled).toBe(false);
      submit().click();
      await settle();

      const create = http.expectOne((req) => req.url === '/api/achievements' && req.method === 'POST');
      expect(create.request.body).toEqual({
        regionId: ZH,
        name: 'Velokurier',
        icon: 'truck',
        visibility: AchievementVisibility.Scope,
        duplicateMode: DuplicateMode.Override,
        validityDays: 30,
        translations: { en: { name: 'Bike courier' } },
      });
      create.flush(achievement({ id: 'new', name: 'Velokurier', icon: 'truck', mayAdminister: true, awardedCount: 0 }));
      await settle();
      http.expectOne(regionList).flush([achievement({ id: 'new', name: 'Velokurier', icon: 'truck', mayAdminister: true, awardedCount: 0 })]);
      await settle();
      expect(q('[data-achievement-id="new"]')).not.toBeNull();
    });

    it('edits in another language without touching the German base text', async () => {
      TestBed.inject(SessionService).applySession(session(Language.Gws));
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      const seed = achievement({
        name: 'Fairteiler-Putzheld (gws)',
        icon: 'handshake',
        mayAdminister: true,
        translations: { gws: { name: 'Fairteiler-Putzheld (gws)' }, en: { name: 'Cleaning hero' } },
      });
      http.expectOne(regionList).flush([seed]);
      await settle();
      click('[data-achievement-id="a3"] [data-testid="achievements.card.edit"]');
      await tick();
      expect(q('[data-testid="achievements.form.baseNote"]')).not.toBeNull();
      // the seed's Lucide icon stays selectable and selected
      expect(q('[data-testid="achievements.form.icon"][data-icon="handshake"]')?.classList).toContain('icon--active');
      click('[data-testid="achievements.form.submit"]');
      await settle();
      const patch = http.expectOne((req) => req.url === '/api/achievements/a3' && req.method === 'PATCH');
      expect(patch.request.body).toEqual({
        icon: 'handshake',
        visibility: AchievementVisibility.Scope,
        duplicateMode: DuplicateMode.Multiple,
        translations: { gws: { name: 'Fairteiler-Putzheld (gws)' }, en: { name: 'Cleaning hero' } },
      });
      patch.flush(seed);
      await settle();
      http.expectOne(regionList).flush([seed]);
      await settle();
    });

    it('awards a member picked by name', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([achievement({ mayAdminister: true, validityDays: 30 })]);
      await settle();
      click('[data-achievement-id="a3"] [data-testid="achievements.card.award"]');
      await tick();
      type('[data-testid="achievements.award.picker.search"]', 'Jul');
      await tick();
      const search = http.expectOne((req) => req.url === '/api/users/search');
      expect(search.request.params.get('regionId')).toBe(ZH);
      search.flush([guide]);
      await settle();
      click('[data-testid="achievements.award.picker.add"]');
      await settle();
      expect(q('[data-testid="achievements.award.recipient"]')?.getAttribute('data-user-id')).toBe(GUIDE);
      type('[data-testid="achievements.award.notice"]', 'Danke!');
      await settle();
      click('[data-testid="achievements.award.submit"]');
      await settle();
      const post = http.expectOne((req) => req.url === '/api/achievements/a3/awards' && req.method === 'POST');
      expect(post.request.body).toEqual({ userId: GUIDE, notice: 'Danke!' });
      post.flush(award());
      await settle();
      http.expectOne(regionList).flush([achievement({ mayAdminister: true, awardedCount: 2 })]);
      await settle();
    });

    it('lists awards, edits one and revokes after confirming', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([achievement({ mayAdminister: true })]);
      await settle();
      click('[data-achievement-id="a3"] [data-testid="achievements.card.awards"]');
      await tick();
      http.expectOne('/api/achievements/a3/awards').flush([
        award({ id: 'old', revokedAt: '2026-09-01T10:00:00Z' }),
        award({ validUntil: '2027-01-01', notice: 'Putztag' }),
      ]);
      await settle();
      const items = all('[data-testid="achievements.awards.item"]');
      expect([...items].map((item) => item.getAttribute('data-status'))).toEqual(['active', 'revoked']);
      expect(q('[data-award-id="old"] [data-testid="achievements.awards.revoke"]')).toBeNull();

      click('[data-award-id="w1"] [data-testid="achievements.awards.edit"]');
      await settle();
      type('[data-testid="achievements.awards.editValidUntil"]', '2027-06-30');
      type('[data-testid="achievements.awards.editNotice"]', '');
      await settle();
      click('[data-testid="achievements.awards.editSave"]');
      await settle();
      const patch = http.expectOne((req) => req.url === '/api/achievements/awards/w1' && req.method === 'PATCH');
      expect(patch.request.body).toEqual({ validUntil: '2027-06-30', notice: '' });
      patch.flush(award({ validUntil: '2027-06-30' }));
      await settle();
      http.expectOne('/api/achievements/a3/awards').flush([award({ validUntil: '2027-06-30' })]);
      await settle();

      const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
      click('[data-award-id="w1"] [data-testid="achievements.awards.revoke"]');
      expect(confirm).toHaveBeenCalledTimes(1);
      confirm.mock.calls[0][0].accept!();
      const revoke = http.expectOne((req) => req.url === '/api/achievements/awards/w1' && req.method === 'DELETE');
      revoke.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      http.expectOne('/api/achievements/a3/awards').flush([award({ revokedAt: '2026-11-02T09:00:00Z' })]);
      http.expectOne(regionList).flush([achievement({ mayAdminister: true, awardedCount: 0 })]);
      await settle();
      expect(q('[data-award-id="w1"]')?.getAttribute('data-status')).toBe('revoked');
    });

    it('shows the awards error state and retries', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([achievement({ mayAdminister: true })]);
      await settle();
      click('[data-achievement-id="a3"] [data-testid="achievements.card.awards"]');
      await tick();
      http.expectOne('/api/achievements/a3/awards').flush(apiHttpError(500, ApiErrorCode.UnexpectedError).error, serverError);
      await settle();
      expect(state('achievements.awards.state')).toBe('error');
      retry('achievements.awards.state');
      await settle();
      http.expectOne('/api/achievements/a3/awards').flush([]);
      await settle();
      expect(state('achievements.awards.state')).toBe('empty');
    });

    it('deletes a regional achievement after confirming', async () => {
      await harness.navigateByUrl(`/regions/${ZH}/achievements`);
      http.expectOne(regionList).flush([achievement({ mayAdminister: true })]);
      await settle();
      const confirm = vi.spyOn(TestBed.inject(ConfirmationService), 'confirm');
      click('[data-achievement-id="a3"] [data-testid="achievements.card.delete"]');
      confirm.mock.calls[0][0].accept!();
      http.expectOne((req) => req.url === '/api/achievements/a3' && req.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      http.expectOne(regionList).flush([]);
      await settle();
      expect(state('achievements.board.state')).toBe('empty');
    });
  });

  describe('admin page', () => {
    it('lists the global achievements; system ones cannot be deleted', async () => {
      await harness.navigateByUrl('/admin/achievements');
      http.expectOne(globalList).flush([
        achievement({ id: 'a1', regionId: undefined, name: 'Hygiene-Zertifikat', icon: 'shield', systemKey: AchievementSystemKey.HygieneCertificate, mayAdminister: true }),
      ]);
      await settle();
      expect(q('[data-testid="achievements.board.define"]')).not.toBeNull();
      expect(q('[data-achievement-id="a1"] [data-testid="achievements.card.system"]')).not.toBeNull();
      expect(q('[data-achievement-id="a1"] [data-testid="achievements.card.edit"]')).not.toBeNull();
      expect(q('[data-achievement-id="a1"] [data-testid="achievements.card.delete"]')).toBeNull();

      // automatic awards have no awarding person
      click('[data-achievement-id="a1"] [data-testid="achievements.card.awards"]');
      await tick();
      http.expectOne('/api/achievements/a1/awards').flush([award({ achievementId: 'a1', awardedBy: undefined })]);
      await settle();
      expect(q('[data-testid="achievements.awards.by"]')?.textContent).toContain('achievements.awards.automatic');
    });
  });
});
