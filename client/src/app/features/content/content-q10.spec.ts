import { HttpTestingController } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { ApiErrorCode, FaqEntry, PublicStatistics, ReleaseNote, TeamGroup } from '../../api';
import { FeatureToggleService } from '../../core/feature-toggles/feature-toggle.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { PetitionBannerComponent } from './components/petition-banner.component';
import { WhatsNewDotComponent } from './components/whats-new-dot.component';
import { CONTENT_ROUTES } from './content.routes';
import { FaqPageComponent } from './pages/faq-page.component';
import { PetitionPageComponent } from './pages/petition-page.component';
import { ReleaseNotesAdminPageComponent } from './pages/release-notes-admin-page.component';
import { ReleaseNotesPageComponent } from './pages/release-notes-page.component';
import { StatisticsPageComponent } from './pages/statistics-page.component';
import { TeamPageComponent } from './pages/team-page.component';
import { PETITION_DISMISSED_KEY } from './petition.resource';
import { RELEASE_NOTES_SEEN_KEY } from './release-notes-seen.service';

const serverError = { status: 500, statusText: 'Server Error' };
const TRANSLATIONS = {
  'content.statistics.asOf': 'Stand: {date}',
  'content.releaseAdmin.incomplete': 'Titel und Text: {languages}',
  'content.petition.bannerCount': '{signatures} Unterschriften, {days} Tage',
};

const FAQ: FaqEntry[] = [
  {
    slug: 'faq-01',
    question: 'Was ist ein Fairteiler?',
    answer: 'Ein öffentlicher **Kühlschrank**.',
  },
  {
    slug: 'faq-02',
    question: 'Wie werde ich Foodsaver?',
    answer: 'Quiz und Einführungsabholungen.',
  },
];

const TEAM: TeamGroup[] = [
  {
    id: 'g1',
    name: 'IT',
    teaser: 'Entwickelt die Plattform.',
    members: [
      { displayName: 'Anna Admin', roleDescription: 'Leitung' },
      { displayName: 'Ben Bot' },
    ],
  },
  { id: 'g2', name: 'Öffentlichkeitsarbeit', members: [] },
];

const STATS: PublicStatistics = {
  kgSaved: 125000.4,
  pickups: 8400,
  foodsavers: 950,
  stores: 120,
  cooperations: 98,
  foodSharePoints: 14,
  baskets: 310,
  asOf: '2026-11-01T02:00:00Z',
};

const note = (version: string, title = `Version ${version}`): ReleaseNote => ({
  id: `id-${version}`,
  version,
  releasedAt: '2026-10-01',
  title,
  body: 'Neu: **alles**.',
});
const NOTES = [note('4.0.0'), note('3.0.0'), note('2.0.0')];

const PETITION = { href: 'https://petition.example/foodsharing', signatures: 4213, daysLeft: 21 };

describe('content pages (Q10)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<unknown>;
  const q = (testId: string) =>
    document.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const all = (testId: string) => document.querySelectorAll(`[data-testid="${testId}"]`);
  const state = (testId: string) => q(testId)?.getAttribute('data-state');
  const settle = () => fixture.whenStable();
  const retry = (testId: string) =>
    (q(testId)!.querySelector('[data-testid$=".retry"] button') as HTMLButtonElement).click();
  const create = async <T>(component: Type<T>) => {
    fixture = TestBed.createComponent(component) as ComponentFixture<unknown>;
    document.body.appendChild(fixture.nativeElement as HTMLElement);
    await settle();
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting({ translations: TRANSLATIONS }), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    (fixture?.nativeElement as HTMLElement | undefined)?.remove();
    localStorage.clear();
  });

  describe('FAQ', () => {
    it('shows the skeleton, then the entries; the filter narrows them and says when nothing matches', async () => {
      await create(FaqPageComponent);
      expect(state('content.faq.state')).toBe('loading');
      const request = http.expectOne((req) => req.url === '/api/public/faq');
      expect(request.request.params.get('lang')).toBe('de');
      request.flush(FAQ);
      await settle();
      expect(state('content.faq.state')).toBe('success');
      expect(all('content.faq.entry')).toHaveLength(2);
      expect(q('content.faq.answer')?.innerHTML).toContain('<strong>Kühlschrank</strong>');

      const filter = q('content.faq.filter') as HTMLInputElement;
      filter.value = 'kuhlschrank';
      filter.dispatchEvent(new Event('input'));
      await settle();
      expect(all('content.faq.entry')).toHaveLength(1);
      expect(q('content.faq.entry')?.getAttribute('data-slug')).toBe('faq-01');

      filter.value = 'nichts passt';
      filter.dispatchEvent(new Event('input'));
      await settle();
      expect(all('content.faq.entry')).toHaveLength(0);
      expect(q('content.faq.noMatch')).not.toBeNull();
    });

    it('shows the empty state without entries', async () => {
      await create(FaqPageComponent);
      http.expectOne((req) => req.url === '/api/public/faq').flush([]);
      await settle();
      expect(state('content.faq.state')).toBe('empty');
    });

    it('shows an error with retry', async () => {
      await create(FaqPageComponent);
      http.expectOne((req) => req.url === '/api/public/faq').flush(null, serverError);
      await settle();
      expect(state('content.faq.state')).toBe('error');
      retry('content.faq.state');
      http.expectOne((req) => req.url === '/api/public/faq').flush(FAQ);
      await settle();
      expect(state('content.faq.state')).toBe('success');
    });
  });

  describe('team', () => {
    it('lists the groups with their members', async () => {
      await create(TeamPageComponent);
      expect(state('content.team.state')).toBe('loading');
      http.expectOne('/api/public/team').flush(TEAM);
      await settle();
      expect(all('content.team.group')).toHaveLength(2);
      expect(all('content.team.member')).toHaveLength(2);
      expect(q('content.team.group')?.textContent).toContain('Leitung');
      expect(all('content.team.groupName')[1].textContent).toContain('Öffentlichkeitsarbeit');
    });

    it('shows the empty state and the error state with retry', async () => {
      await create(TeamPageComponent);
      http.expectOne('/api/public/team').flush(null, serverError);
      await settle();
      expect(state('content.team.state')).toBe('error');
      retry('content.team.state');
      http.expectOne('/api/public/team').flush([]);
      await settle();
      expect(state('content.team.state')).toBe('empty');
    });
  });

  describe('statistics', () => {
    it('shows seven counters with the aggregated values and the time of the aggregation', async () => {
      await create(StatisticsPageComponent);
      expect(state('content.statistics.state')).toBe('loading');
      http.expectOne('/api/public/statistics').flush(STATS);
      await settle();
      expect(all('content.statistics.tile')).toHaveLength(7);
      expect(q('content.statistics.kgSaved')?.getAttribute('data-value')).toBe('125000.4');
      expect(q('content.statistics.baskets')?.getAttribute('data-value')).toBe('310');
      expect(q('content.statistics.asOf')?.textContent).toContain('2026');
    });

    it('shows an error with retry', async () => {
      await create(StatisticsPageComponent);
      http.expectOne('/api/public/statistics').flush(null, serverError);
      await settle();
      expect(state('content.statistics.state')).toBe('error');
      retry('content.statistics.state');
      http.expectOne('/api/public/statistics').flush(STATS);
      await settle();
      expect(state('content.statistics.state')).toBe('success');
    });
  });

  describe('release notes', () => {
    it('marks notes newer than the seen version and remembers the newest one', async () => {
      localStorage.setItem(RELEASE_NOTES_SEEN_KEY, '2.0.0');
      await create(ReleaseNotesPageComponent);
      expect(state('content.releaseNotes.state')).toBe('loading');
      http.expectOne((req) => req.url === '/api/public/release-notes').flush(NOTES);
      await settle();
      expect(all('content.releaseNotes.note')).toHaveLength(3);
      expect(all('content.releaseNotes.new')).toHaveLength(2);
      expect(localStorage.getItem(RELEASE_NOTES_SEEN_KEY)).toBe('4.0.0');
    });

    it('shows the empty state and the error state with retry', async () => {
      await create(ReleaseNotesPageComponent);
      http.expectOne((req) => req.url === '/api/public/release-notes').flush(null, serverError);
      await settle();
      expect(state('content.releaseNotes.state')).toBe('error');
      retry('content.releaseNotes.state');
      http.expectOne((req) => req.url === '/api/public/release-notes').flush([]);
      await settle();
      expect(state('content.releaseNotes.state')).toBe('empty');
      expect(localStorage.getItem(RELEASE_NOTES_SEEN_KEY)).toBeNull();
    });

    it('header dot: shown while the newest note is unseen, gone once it is seen', async () => {
      localStorage.setItem(RELEASE_NOTES_SEEN_KEY, '3.0.0');
      await create(WhatsNewDotComponent);
      http.expectOne('/api/public/release-notes').flush(NOTES);
      await settle();
      expect(q('content.releaseNotes.dot')).not.toBeNull();
      fixture.destroy();

      localStorage.setItem(RELEASE_NOTES_SEEN_KEY, '4.0.0');
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [...provideDataStateTesting({ translations: TRANSLATIONS }), provideOptimus()],
      });
      http = TestBed.inject(HttpTestingController);
      await create(WhatsNewDotComponent);
      http.expectOne('/api/public/release-notes').flush(NOTES);
      await settle();
      expect(q('content.releaseNotes.dot')).toBeNull();
    });

    it('header dot: no dot when loading fails', async () => {
      await create(WhatsNewDotComponent);
      http.expectOne('/api/public/release-notes').flush(null, serverError);
      await settle();
      expect(q('content.releaseNotes.dot')).toBeNull();
    });
  });

  describe('release notes admin', () => {
    const open = async () => {
      await create(ReleaseNotesAdminPageComponent);
      expect(state('content.releaseAdmin.state')).toBe('loading');
      http.expectOne('/api/release-notes').flush(NOTES);
      await settle();
    };
    const type = (testId: string, value: string) => {
      const input = q(testId) as HTMLInputElement | HTMLTextAreaElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const click = (testId: string) => {
      const host = q(testId)!;
      ((host.querySelector('button') ?? host) as HTMLElement).click();
    };

    it('creates a note with German text and complete translations only', async () => {
      await open();
      expect(all('content.releaseAdmin.note')).toHaveLength(3);
      click('content.releaseAdmin.new');
      await settle();
      type('content.releaseAdmin.version', '5.0.0');
      type('content.releaseAdmin.titles.field.de', 'Neu');
      type('content.releaseAdmin.bodies.field.de', 'Text');
      click('content.releaseAdmin.titles.tab.fr');
      await settle();
      type('content.releaseAdmin.titles.field.fr', 'Nouveau');
      await settle();
      // a title without text blocks saving
      expect(q('content.releaseAdmin.incomplete')?.textContent).toContain('FR');
      expect(
        (q('content.releaseAdmin.save')!.querySelector('button') as HTMLButtonElement).disabled,
      ).toBe(true);
      click('content.releaseAdmin.bodies.tab.fr');
      await settle();
      type('content.releaseAdmin.bodies.field.fr', 'Texte');
      await settle();
      expect(q('content.releaseAdmin.incomplete')).toBeNull();
      click('content.releaseAdmin.save');
      await settle();
      const request = http.expectOne(
        (req) => req.method === 'POST' && req.url === '/api/release-notes',
      );
      expect(request.request.body).toEqual(
        expect.objectContaining({
          version: '5.0.0',
          title: 'Neu',
          body: 'Text',
          translations: { fr: { title: 'Nouveau', body: 'Texte' } },
        }),
      );
      expect(request.request.body.releasedAt).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      request.flush(note('5.0.0'));
      await settle();
      http.expectOne('/api/release-notes').flush([note('5.0.0'), ...NOTES]);
      await settle();
      expect(all('content.releaseAdmin.note')).toHaveLength(4);
    });

    it('edits a note with its German text prefilled and deletes after confirmation', async () => {
      await open();
      (all('content.releaseAdmin.edit')[1].querySelector('button') as HTMLButtonElement).click();
      await settle();
      expect((q('content.releaseAdmin.version') as HTMLInputElement).value).toBe('3.0.0');
      expect((q('content.releaseAdmin.titles.field.de') as HTMLInputElement).value).toBe(
        'Version 3.0.0',
      );
      expect(q('content.releaseAdmin.translationsHint')).not.toBeNull();
      type('content.releaseAdmin.titles.field.de', 'Betriebe');
      click('content.releaseAdmin.save');
      await settle();
      const update = http.expectOne(
        (req) => req.method === 'PATCH' && req.url === '/api/release-notes/id-3.0.0',
      );
      expect(update.request.body.title).toBe('Betriebe');
      expect(update.request.body).not.toHaveProperty('translations');
      update.flush(note('3.0.0', 'Betriebe'));
      await settle();
      http.expectOne('/api/release-notes').flush(NOTES);
      await settle();

      const confirm = TestBed.inject(ConfirmationService);
      const accept = vi.spyOn(confirm, 'confirm').mockImplementation((options) => {
        options.accept?.();
        return confirm;
      });
      (all('content.releaseAdmin.delete')[2].querySelector('button') as HTMLButtonElement).click();
      await settle();
      expect(accept).toHaveBeenCalled();
      http
        .expectOne((req) => req.method === 'DELETE' && req.url === '/api/release-notes/id-2.0.0')
        .flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      http.expectOne('/api/release-notes').flush(NOTES.slice(0, 2));
      await settle();
      expect(all('content.releaseAdmin.note')).toHaveLength(2);
    });

    it('shows the error state with retry and the empty state', async () => {
      await create(ReleaseNotesAdminPageComponent);
      http.expectOne('/api/release-notes').flush(null, serverError);
      await settle();
      expect(state('content.releaseAdmin.state')).toBe('error');
      retry('content.releaseAdmin.state');
      http.expectOne('/api/release-notes').flush([]);
      await settle();
      expect(state('content.releaseAdmin.state')).toBe('empty');
    });
  });

  describe('petition', () => {
    it('page: shows signatures, days left and the sign link', async () => {
      await create(PetitionPageComponent);
      expect(state('content.petition.state')).toBe('loading');
      http.expectOne('/api/public/petition').flush(PETITION);
      await settle();
      expect(q('content.petition.signatures')?.getAttribute('data-value')).toBe('4213');
      expect(q('content.petition.daysLeft')?.textContent).toContain('21');
      expect(q('content.petition.sign')?.getAttribute('href')).toBe(PETITION.href);
    });

    it('page: no running petition is the empty state, a switched-off toggle is not found', async () => {
      await create(PetitionPageComponent);
      const inactive = apiHttpError(503, ApiErrorCode.PetitionInactive);
      http
        .expectOne('/api/public/petition')
        .flush(inactive.error, { status: 503, statusText: 'Service Unavailable' });
      await settle();
      expect(state('content.petition.state')).toBe('empty');
      fixture.destroy();

      await create(PetitionPageComponent);
      const disabled = apiHttpError(404, ApiErrorCode.FeatureDisabled);
      http
        .expectOne('/api/public/petition')
        .flush(disabled.error, { status: 404, statusText: 'Not Found' });
      await settle();
      expect(state('content.petition.state')).toBe('notFound');
    });

    it('banner: loads nothing while PETITION_BANNER is off', async () => {
      const toggles = TestBed.inject(FeatureToggleService);
      void toggles.load();
      http.expectOne('/api/public/feature-toggles').flush({ PETITION_BANNER: false });
      await toggles.load();
      await create(PetitionBannerComponent);
      http.expectNone('/api/public/petition');
      expect(q('content.petition.banner')).toBeNull();
    });

    it('banner: shown while the toggle is on, hidden for an inactive petition and after dismissing', async () => {
      const toggles = TestBed.inject(FeatureToggleService);
      void toggles.load();
      http.expectOne('/api/public/feature-toggles').flush({ PETITION_BANNER: true });
      await toggles.load();
      await create(PetitionBannerComponent);
      http.expectOne('/api/public/petition').flush(PETITION);
      await settle();
      expect(q('content.petition.banner')).not.toBeNull();
      expect(q('content.petition.bannerCount')?.textContent).toMatch(
        /4.213 Unterschriften, 21 Tage/,
      );
      expect(q('content.petition.bannerSign')?.getAttribute('href')).toBe(PETITION.href);

      q('content.petition.bannerDismiss')!.click();
      await settle();
      expect(q('content.petition.banner')).toBeNull();
      expect(localStorage.getItem(PETITION_DISMISSED_KEY)).toBe(PETITION.href);
      fixture.destroy();

      // a new petition (other link) shows again
      await create(PetitionBannerComponent);
      http
        .expectOne('/api/public/petition')
        .flush({ ...PETITION, href: 'https://petition.example/new' });
      await settle();
      expect(q('content.petition.banner')).not.toBeNull();
      fixture.destroy();

      localStorage.clear();
      await create(PetitionBannerComponent);
      const inactive = apiHttpError(503, ApiErrorCode.PetitionInactive);
      http
        .expectOne('/api/public/petition')
        .flush(inactive.error, { status: 503, statusText: 'Service Unavailable' });
      await settle();
      expect(q('content.petition.banner')).toBeNull();
    });
  });

  describe('routes', () => {
    it('serve the about pages with their slugs and redirect body links to the canonical route', async () => {
      const cities = CONTENT_ROUTES.find((route) => route.path === 'about/cities');
      expect(cities?.data).toEqual({ slug: 'foodsharing-cities' });
      expect(CONTENT_ROUTES.find((route) => route.path === 'for-companies')?.data).toEqual({
        slug: 'for-companies',
      });

      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        providers: [...provideDataStateTesting({ routes: [...CONTENT_ROUTES] }), provideOptimus()],
      });
      const router = TestBed.inject(Router);
      http = TestBed.inject(HttpTestingController);
      await router.navigateByUrl('/content/for-companies');
      expect(router.url).toBe('/for-companies');
      http
        .match(() => true)
        .forEach((request) =>
          request.flush({
            slug: 'for-companies',
            title: 'x',
            body: 'y',
            language: 'de',
            fallback: false,
          }),
        );
    });
  });
});
