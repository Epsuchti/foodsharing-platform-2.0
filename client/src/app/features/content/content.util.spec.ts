import { FaqEntry, ReleaseNote } from '../../api';
import { ABOUT_PAGES, contentPath } from './content-paths';
import {
  filterFaq,
  foldText,
  hasUnseenReleaseNote,
  releaseNoteRequest,
  statisticsTiles,
  unseenVersions,
  zurichDay,
} from './content.util';
import { petitionBannerAllowedOn, safePetitionHref } from './petition.resource';

const note = (version: string): ReleaseNote => ({
  id: `id-${version}`,
  version,
  releasedAt: '2026-10-01',
  title: `Version ${version}`,
  body: 'Text',
});

describe('content rules (Q10)', () => {
  describe('FAQ filter', () => {
    const entries: FaqEntry[] = [
      {
        slug: 'faq-01',
        question: 'Was ist ein Fairteiler?',
        answer: 'Ein öffentlicher Kühlschrank.',
      },
      {
        slug: 'faq-02',
        question: 'Wie werde ich Foodsaver?',
        answer: 'Quiz, Einführungsabholungen, Ausweis.',
      },
      { slug: 'faq-03', question: 'Où trouver un frigo partagé ?', answer: 'Sur la carte.' },
    ];

    it('keeps everything for an empty query', () => {
      expect(filterFaq(entries, '   ').map((e) => e.slug)).toEqual(['faq-01', 'faq-02', 'faq-03']);
    });

    it('matches every word in question or answer, ignoring case and accents', () => {
      expect(filterFaq(entries, 'fairteiler').map((e) => e.slug)).toEqual(['faq-01']);
      expect(filterFaq(entries, 'KUHLSCHRANK').map((e) => e.slug)).toEqual(['faq-01']);
      expect(filterFaq(entries, 'ou partage').map((e) => e.slug)).toEqual(['faq-03']);
      expect(filterFaq(entries, 'foodsaver ausweis').map((e) => e.slug)).toEqual(['faq-02']);
      expect(filterFaq(entries, 'foodsaver kühlschrank')).toEqual([]);
    });

    it('folds text to lower case without accents', () => {
      expect(foldText('Écrire Über')).toBe('ecrire uber');
    });
  });

  describe('release notes seen', () => {
    const notes = [note('4.0.0'), note('3.0.0'), note('2.0.0')];

    it('marks only the newest note new on a first visit', () => {
      expect([...unseenVersions(notes, null)]).toEqual(['4.0.0']);
    });

    it('marks every note newer than the seen one', () => {
      expect([...unseenVersions(notes, '2.0.0')]).toEqual(['4.0.0', '3.0.0']);
      expect([...unseenVersions(notes, '4.0.0')]).toEqual([]);
    });

    it('treats an unknown seen version (deleted note) like a first visit', () => {
      expect([...unseenVersions(notes, '9.9.9')]).toEqual(['4.0.0']);
      expect(unseenVersions([], '1.0.0').size).toBe(0);
    });

    it('shows the header dot while the newest version differs from the seen one', () => {
      expect(hasUnseenReleaseNote('4.0.0', null)).toBe(true);
      expect(hasUnseenReleaseNote('4.0.0', '3.0.0')).toBe(true);
      expect(hasUnseenReleaseNote('4.0.0', '4.0.0')).toBe(false);
      expect(hasUnseenReleaseNote(undefined, null)).toBe(false);
    });
  });

  describe('release note request', () => {
    it('sends German as the main text and complete other languages as translations', () => {
      const { body, incomplete } = releaseNoteRequest({
        version: ' 5.0.0 ',
        releasedAt: '2026-11-02',
        titles: { de: ' Neu ', fr: 'Nouveau', it: '' },
        bodies: { de: 'Text', fr: 'Texte' },
      });
      expect(incomplete).toEqual([]);
      expect(body).toEqual({
        version: '5.0.0',
        releasedAt: '2026-11-02',
        title: 'Neu',
        body: 'Text',
        translations: { fr: { title: 'Nouveau', body: 'Texte' } },
      });
    });

    it('omits translations when only German is filled and lists half-filled languages', () => {
      expect(
        releaseNoteRequest({
          version: '1',
          releasedAt: '2026-01-01',
          titles: { de: 'T' },
          bodies: { de: 'B' },
        }).body,
      ).not.toHaveProperty('translations');
      expect(
        releaseNoteRequest({
          version: '1',
          releasedAt: '2026-01-01',
          titles: { de: 'T', it: 'Titolo', en: '' },
          bodies: { de: 'B', gws: 'Text' },
        }).incomplete,
      ).toEqual(['gws', 'it']);
    });

    it('removes saved translations that were emptied completely, keeps the half-filled ones as errors', () => {
      const { body, incomplete } = releaseNoteRequest(
        {
          version: '1',
          releasedAt: '2026-01-01',
          titles: { de: 'T', fr: '', it: 'Titolo', en: ' ' },
          bodies: { de: 'B', fr: '', it: 'Testo', en: '', gws: 'Text' },
        },
        ['fr', 'it', 'en', 'gws'],
      );
      expect(body.removeTranslations).toEqual(['en', 'fr']);
      expect(body.translations).toEqual({ it: { title: 'Titolo', body: 'Testo' } });
      expect(incomplete).toEqual(['gws']);
      expect(releaseNoteRequest({ version: '1', releasedAt: '2026-01-01', titles: { de: 'T' }, bodies: { de: 'B' } }).body).not.toHaveProperty(
        'removeTranslations',
      );
    });

    it('defaults the release date to the Zurich day', () => {
      expect(zurichDay(new Date('2026-11-01T23:30:00Z'))).toBe('2026-11-02');
      expect(zurichDay(new Date('2026-11-02T10:00:00Z'))).toBe('2026-11-02');
    });
  });

  it('builds seven statistics tiles in display order without negative values', () => {
    const tiles = statisticsTiles({
      kgSaved: 1234.6,
      pickups: 10,
      foodsavers: 3,
      stores: 2,
      cooperations: 1,
      foodSharePoints: -1,
      baskets: 4,
      asOf: '2026-11-01T02:00:00Z',
    });
    expect(tiles.map((tile) => tile.key)).toEqual([
      'kgSaved',
      'pickups',
      'foodsavers',
      'stores',
      'cooperations',
      'foodSharePoints',
      'baskets',
    ]);
    expect(tiles[0].value).toBe(1234.6);
    expect(tiles[5].value).toBe(0);
  });

  it('renders only http(s) petition links', () => {
    expect(safePetitionHref(' https://petition.example/x ')).toBe('https://petition.example/x');
    expect(safePetitionHref('javascript:alert(1)')).toBeNull();
    expect(safePetitionHref('')).toBeNull();
  });

  it('keeps the petition banner off the consent and petition pages', () => {
    for (const url of ['/consent', '/consent?returnUrl=%2Fdashboard', '/petition']) {
      expect(petitionBannerAllowedOn(url), url).toBe(false);
    }
    for (const url of ['/', '/dashboard', '/consents', '/legal/privacy-policy', '/petitions-faq']) {
      expect(petitionBannerAllowedOn(url), url).toBe(true);
    }
  });

  it('maps CMS slugs to their canonical routes', () => {
    expect(contentPath('foodsharing-cities')).toBe('/about/cities');
    expect(contentPath('for-companies')).toBe('/for-companies');
    expect(contentPath('imprint')).toBe('/imprint');
    expect(contentPath('donation-campaign-1')).toBe('/pages/donation-campaign-1');
    expect(new Set(ABOUT_PAGES.map((page) => page.path)).size).toBe(ABOUT_PAGES.length);
  });
});
