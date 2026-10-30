import { HttpTestingController } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { ApiErrorCode, ContentPageKind, DonationPage, Language } from '../../api';
import { SessionService } from '../../core/auth/session.service';
import { FeatureToggleService } from '../../core/feature-toggles/feature-toggle.service';
import { apiHttpError, provideDataStateTesting } from '../../core/testing/data-state-testing';
import { sessionInfo } from '../auth/testing';
import { DonationModalComponent } from './components/donation-modal.component';
import { contentImageUrls, imageMarkdown } from './content-images';
import {
  DONATION_MODAL_SHOWN_KEY,
  donationFrames,
  donationModalDue,
  donationPageEmpty,
  safeHttpsUrl,
} from './donation.util';
import { ContentEditorPageComponent } from './pages/content-editor-page.component';
import { DonatePageComponent } from './pages/donate-page.component';
import { SupportPageComponent } from './pages/support-page.component';
import { plausibleEmail, supportSourcePage } from './support.util';

const HOUR = 3_600_000;
const NOW = Date.parse('2026-11-02T09:00:00Z');

const DONATION: DonationPage = {
  showCampaignCard: true,
  showModal: true,
  modalHoursLoggedIn: 168,
  modalHoursLoggedOut: 24,
  infoUrl: 'https://foodsharing.example/spenden',
  iframeOneTimeUrl: 'https://pay.example/once',
  iframeCampaignUrl: 'http://pay.example/insecure',
  iframeFriendshipCircleUrl: '',
  iframeSelfServiceUrl: 'javascript:alert(1)',
  campaignPages: [
    {
      slug: 'donation-campaign-1',
      title: 'Winteraktion',
      body: 'Hilf uns **jetzt**.',
      language: Language.De,
      fallback: false,
    },
  ],
};

describe('content rules (Q11)', () => {
  it('donation modal: only with showModal, once per hours setting (logged in / out), 0 = every visit', () => {
    expect(donationModalDue({ ...DONATION, showModal: false }, false, null, NOW)).toBe(false);
    expect(donationModalDue(DONATION, false, null, NOW)).toBe(true);
    expect(donationModalDue(DONATION, false, String(NOW - 23 * HOUR), NOW)).toBe(false);
    expect(donationModalDue(DONATION, false, String(NOW - 24 * HOUR), NOW)).toBe(true);
    expect(donationModalDue(DONATION, true, String(NOW - 24 * HOUR), NOW)).toBe(false);
    expect(donationModalDue(DONATION, true, String(NOW - 168 * HOUR), NOW)).toBe(true);
    expect(donationModalDue({ ...DONATION, modalHoursLoggedOut: 0 }, false, String(NOW), NOW)).toBe(true);
    // garbage or a time in the future counts as never shown
    expect(donationModalDue(DONATION, false, 'x', NOW)).toBe(true);
    expect(donationModalDue(DONATION, false, String(NOW + HOUR), NOW)).toBe(true);
  });

  it('donation frames: https only, in display order; empty page without campaign and frames', () => {
    expect(donationFrames(DONATION)).toEqual([{ kind: 'oneTime', url: 'https://pay.example/once' }]);
    expect(safeHttpsUrl(' https://a.example/x ')).toBe('https://a.example/x');
    expect(safeHttpsUrl('/relative')).toBeNull();
    expect(donationPageEmpty(DONATION)).toBe(false);
    expect(
      donationPageEmpty({ ...DONATION, showCampaignCard: false, iframeOneTimeUrl: undefined }),
    ).toBe(true);
    expect(donationPageEmpty({ ...DONATION, campaignPages: [], iframeOneTimeUrl: '' })).toBe(true);
  });

  it('support source page: app paths from ?from=, same-site referrers, nothing else', () => {
    const origin = 'https://foodsharing.example';
    expect(supportSourcePage('/stores/1?tab=x', null, origin)).toBe('/stores/1?tab=x');
    expect(supportSourcePage('//evil.example', null, origin)).toBe('');
    expect(supportSourcePage(null, `${origin}/dashboard?x=1`, origin)).toBe('/dashboard?x=1');
    expect(supportSourcePage(null, `${origin}/support`, origin)).toBe('');
    expect(supportSourcePage(null, 'https://other.example/x', origin)).toBe('');
    expect(supportSourcePage('/' + 'a'.repeat(600), null, origin)).toHaveLength(500);
    expect(plausibleEmail('a@b')).toBe(true);
    expect(plausibleEmail('a b@c')).toBe(false);
  });

  it('page images: large variant in the body, Markdown without brackets in the alt text', () => {
    expect(contentImageUrls('u1')).toEqual({
      url: '/api/uploads/u1?size=lg',
      thumb: '/api/uploads/u1?size=sm',
    });
    expect(imageMarkdown('/api/uploads/u1?size=lg', 'a [b]')).toBe('![a b](/api/uploads/u1?size=lg)');
  });
});

describe('content pages (Q11)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<unknown>;
  const q = (testId: string) =>
    document.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const all = (testId: string) => document.querySelectorAll(`[data-testid="${testId}"]`);
  const state = (testId: string) => q(testId)?.getAttribute('data-state');
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
  };
  const create = async <T>(component: Type<T>) => {
    fixture = TestBed.createComponent(component) as ComponentFixture<unknown>;
    document.body.appendChild(fixture.nativeElement as HTMLElement);
    await settle();
  };
  const type = (testId: string, value: string) => {
    const input = q(testId) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [...provideDataStateTesting(), provideOptimus()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify({ ignoreCancelled: true });
    (fixture?.nativeElement as HTMLElement | undefined)?.remove();
    document.querySelectorAll('.p-dialog-mask').forEach((node) => node.remove());
    localStorage.clear();
  });

  describe('donate', () => {
    it('shows the campaign, only the https donation forms and the info link', async () => {
      await create(DonatePageComponent);
      expect(state('content.donate.state')).toBe('loading');
      const request = http.expectOne((req) => req.url === '/api/public/donations');
      expect(request.request.params.get('lang')).toBe('de');
      request.flush(DONATION);
      await settle();
      expect(state('content.donate.state')).toBe('success');
      expect(all('content.donate.campaign')).toHaveLength(1);
      expect(q('content.donate.campaign')?.innerHTML).toContain('<strong>jetzt</strong>');
      const frames = all('content.donate.iframe');
      expect(frames).toHaveLength(1);
      expect(frames[0].getAttribute('src')).toBe('https://pay.example/once');
      expect(q('content.donate.info')?.getAttribute('href')).toBe(DONATION.infoUrl);
    });

    it('shows the empty state without campaign and forms, and the error state with retry', async () => {
      await create(DonatePageComponent);
      http
        .expectOne((req) => req.url === '/api/public/donations')
        .flush({ ...DONATION, showCampaignCard: false, iframeOneTimeUrl: undefined });
      await settle();
      expect(state('content.donate.state')).toBe('empty');
      fixture.destroy();

      await create(DonatePageComponent);
      http
        .expectOne((req) => req.url === '/api/public/donations')
        .flush(null, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(state('content.donate.state')).toBe('error');
      (q('content.donate.state')!.querySelector('[data-testid$=".retry"] button') as HTMLButtonElement).click();
      http.expectOne((req) => req.url === '/api/public/donations').flush(DONATION);
      await settle();
      expect(state('content.donate.state')).toBe('success');
    });
  });

  describe('donation modal', () => {
    const enable = async (on: boolean) => {
      const toggles = TestBed.inject(FeatureToggleService);
      void toggles.load();
      http.expectOne('/api/public/feature-toggles').flush({ DONATION_MODAL: on });
      await toggles.load();
    };

    it('loads nothing while DONATION_MODAL is off', async () => {
      await enable(false);
      await create(DonationModalComponent);
      http.expectNone('/api/public/donations');
      expect(q('content.donationModal.body')).toBeNull();
    });

    it('opens when due, shows the donation-modal page and remembers the time', async () => {
      await enable(true);
      await create(DonationModalComponent);
      http.expectOne((req) => req.url === '/api/public/donations').flush(DONATION);
      await settle();
      http
        .expectOne((req) => req.url === '/api/public/content/donation-modal')
        .flush({ slug: 'donation-modal', title: 'Spende jetzt', body: 'Danke!', language: 'de', fallback: false });
      await settle();
      expect(q('content.donationModal.body')?.textContent).toContain('Danke!');
      expect(Number(localStorage.getItem(DONATION_MODAL_SHOWN_KEY))).toBeGreaterThan(0);
    });

    it('stays closed while the last showing is recent', async () => {
      localStorage.setItem(DONATION_MODAL_SHOWN_KEY, String(Date.now() - HOUR));
      await enable(true);
      await create(DonationModalComponent);
      http.expectOne((req) => req.url === '/api/public/donations').flush(DONATION);
      await settle();
      http.expectNone((req) => req.url === '/api/public/content/donation-modal');
      expect(q('content.donationModal.body')).toBeNull();
    });
  });

  describe('support', () => {
    it('logged out: needs name and email, sends page and user agent, shows the ticket', async () => {
      await create(SupportPageComponent);
      const send = () => (q('content.support.send')!.querySelector('button') as HTMLButtonElement);
      expect(q('content.support.files.input')).toBeNull();
      expect((q('content.support.userAgent') as HTMLInputElement).readOnly).toBe(true);
      type('content.support.subject', 'Login geht nicht');
      type('content.support.body', 'Der Link kommt nicht an.');
      await settle();
      expect(send().disabled).toBe(true);
      type('content.support.firstName', 'Mia');
      type('content.support.email', 'mia@example.org');
      await settle();
      expect(send().disabled).toBe(false);
      send().click();
      const request = http.expectOne('/api/public/support/tickets');
      expect(request.request.body).toMatchObject({
        subject: 'Login geht nicht',
        body: 'Der Link kommt nicht an.',
        firstName: 'Mia',
        email: 'mia@example.org',
        userAgent: navigator.userAgent.slice(0, 500),
      });
      request.flush({ ticketId: 't-1' });
      await settle();
      expect(q('content.support.ticketId')?.textContent).toBe('t-1');
    });

    it('signed in: sends to the member endpoint; 429 SUPPORT_RATE_LIMITED keeps the form with a notice', async () => {
      TestBed.inject(SessionService).applySession(sessionInfo());
      await create(SupportPageComponent);
      expect(q('content.support.firstName')).toBeNull();
      expect(q('content.support.files.input')).not.toBeNull();
      type('content.support.subject', 'Frage');
      type('content.support.body', 'Text');
      await settle();
      (q('content.support.send')!.querySelector('button') as HTMLButtonElement).click();
      const limited = apiHttpError(429, ApiErrorCode.SupportRateLimited);
      const request = http.expectOne('/api/support/tickets');
      expect(request.request.body).toMatchObject({ subject: 'Frage', attachmentUploadIds: [] });
      request.flush(limited.error, { status: 429, statusText: 'Too Many Requests' });
      await settle();
      expect(q('content.support.rateLimited')).not.toBeNull();
      expect(q('content.support.sent')).toBeNull();
      expect((q('content.support.subject') as HTMLInputElement).value).toBe('Frage');
    });
  });

  describe('editor images (decision 117)', () => {
    it('removes a claimed image on save with removeImageUploadIds; nothing is sent without a removal', async () => {
      fixture = TestBed.createComponent(ContentEditorPageComponent) as ComponentFixture<unknown>;
      fixture.componentRef.setInput('slug', 'about');
      document.body.appendChild(fixture.nativeElement as HTMLElement);
      await settle();
      http.expectOne('/api/content/pages/about').flush({
        titles: { de: 'Über uns' },
        bodies: { de: 'Text' },
        kind: ContentPageKind.Page,
        published: true,
        imageUploadIds: ['img-1', 'img-2'],
      });
      await settle();
      expect(all('content.editor.image')).toHaveLength(2);
      expect(q('content.editor.imagesRemoved')).toBeNull();

      const first = q('content.editor.image')!;
      expect(first.getAttribute('data-id')).toBe('img-1');
      (first.querySelector('[data-testid="content.editor.removeImage"] button') as HTMLButtonElement).click();
      await settle();
      expect([...all('content.editor.image')].map((item) => item.getAttribute('data-id'))).toEqual(['img-2']);
      expect(q('content.editor.imagesRemoved')).not.toBeNull();

      (q('content.editor.save')!.querySelector('button') as HTMLButtonElement).click();
      const save = http.expectOne((req) => req.url === '/api/content/pages/about' && req.method === 'PUT');
      expect(save.request.body.removeImageUploadIds).toEqual(['img-1']);
      expect(save.request.body.imageUploadIds).toBeUndefined();
      save.flush({ slug: 'about', title: 'Über uns', kind: ContentPageKind.Page, published: true });
      await settle();
      http.expectOne('/api/content/pages/about').flush({
        titles: { de: 'Über uns' },
        bodies: { de: 'Text' },
        kind: ContentPageKind.Page,
        published: true,
        imageUploadIds: ['img-2'],
      });
      await settle();
      expect(q('content.editor.imagesRemoved')).toBeNull();

      (q('content.editor.save')!.querySelector('button') as HTMLButtonElement).click();
      const again = http.expectOne((req) => req.url === '/api/content/pages/about' && req.method === 'PUT');
      expect(again.request.body.removeImageUploadIds).toBeUndefined();
      again.flush({ slug: 'about', title: 'Über uns', kind: ContentPageKind.Page, published: true });
      await settle();
      http.expectOne('/api/content/pages/about').flush({
        titles: { de: 'Über uns' },
        bodies: { de: 'Text' },
        kind: ContentPageKind.Page,
        published: true,
        imageUploadIds: ['img-2'],
      });
      await settle();
    });
  });
});
