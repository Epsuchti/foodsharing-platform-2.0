import type { ApiClient } from '../../fixtures/api';
import type { Role } from '../../support/env';

/**
 * Route table of the axe sweep (`pages.spec.ts`, plan §18.3, plan-m7 F6): every route of the app as the role that
 * normally sees it. `scripts/a11y-route-coverage.mjs` checks it against the real route tree of the client and fails
 * when a route has neither an entry here nor an exclusion in SWEEP_EXCLUDED. Pure data: no Playwright imports, the
 * coverage script bundles this file for Node.
 */

export interface RouteCase {
  path: string;
  role: Role | null;
  /** Something that proves the page rendered (beyond the shell). */
  ready?: string;
  /**
   * Accept pending legal consents first (default for every signed-in role; the consent page itself and a fresh
   * newbie must see them), so the page itself is swept and not the consent redirect.
   */
  consent?: boolean;
  /** Data states other than success/empty that are expected on this page (e.g. a token error). */
  states?: ('error' | 'forbidden' | 'notFound' | 'offline')[];
  /** Prepares the state the page shows, as the route's user (after consents, before the page opens). */
  setup?: (api: ApiClient) => Promise<unknown>;
  /** Also swept on a phone (Pixel 7): the top-level pages of the navigation. */
  mobile?: boolean;
}

export interface Exclusion {
  /** Route pattern as the router sees it; `*` matches anything. */
  pattern: string;
  reason: string;
}

const ZH = '00000010-0001-4000-8000-000000000040';
const user = (n: number) => `00000001-0001-4000-8000-${String(n).padStart(12, '0')}`;
const FOODSAVER = user(6);
const QUIZ_USER = user(3);
const SLEEPY = user(8);
const KOCHGRUPPE = '00000011-0001-4000-8000-000000000033';
const S01 = '00000020-0001-4000-8000-000000000001';
const COOP_CHAIN = '00000020-0003-4000-8000-000000000001';
const MAILBOX_ZH = '00000042-0001-4000-8000-000000000001';
const MAIL_5 = '00000042-0003-4000-8000-000000000005';
const CONVERSATION = '00000041-0001-4000-8000-000000000001';
const THREAD = '00000043-0001-4000-8000-000000000001';
const EVENT = '00000044-0002-4000-8000-000000000001';
const POLL = '00000045-0001-4000-8000-000000000001';
const SURVEY = '00000046-0001-4000-8000-000000000001';
const NEWSLETTER = '00000004-0006-4000-8000-000000000001';
const BASKET_BROT = '00000048-0001-4000-8000-000000000001';
const FSP_WIEDIKON = '00000049-0001-4000-8000-000000000001';

/**
 * Starts an authorization request of the public seed client `mobile-dev` (no remembered consent) and returns the
 * consent page `/oauth/authorize` redirects to (fixtures/oauth.ts does the same with a fresh PKCE pair; this file
 * stays free of Node/Playwright imports for the coverage script).
 */
export async function consentRequest(api: ApiClient): Promise<string> {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: 'mobile-dev',
    redirect_uri: 'http://localhost:8080/dev/oauth-callback',
    scope: 'openid profile',
    code_challenge: 'Z3_WMgKJ-vanY78-e0-noChyK-_awhaix-Kbn5pKM1Q',
    code_challenge_method: 'S256',
  });
  const response = await api.request.get(api.url(`/oauth/authorize?${params}`), { maxRedirects: 0 });
  const location = response.headers()['location'] ?? '';
  if (response.status() !== 302 || !location.includes('/oauth/consent?request=')) {
    throw new Error(`authorize answered ${response.status()} → ${location}`);
  }
  return new URL(location, api.baseURL).pathname + new URL(location, api.baseURL).search;
}

export const SWEEP_ROUTES: RouteCase[] = [
  // --- public pages and the shell
  { path: '/', role: null, ready: 'public.landing.hero', mobile: true },
  ...(['de', 'gws', 'fr', 'it', 'en'] as const).map((lang) => ({ path: `/${lang}`, role: null, ready: 'public.landing.hero' })),
  { path: '/login', role: null, mobile: true },
  { path: '/login/sent?email=newbie%40local', role: null },
  // without a token: the verify page shows its error state
  { path: '/login/verify?token=not-a-token', role: null, ready: 'auth.verify.tokenError', states: ['error'] },
  { path: '/forbidden', role: null },
  { path: '/does-not-exist', role: null },
  { path: '/dev/gallery', role: null, ready: 'gallery.dataState.live' },
  { path: '/consent', role: 'newbie', ready: 'legal.consent.documents', consent: false },
  { path: '/legal/privacy-policy', role: null, ready: 'legal.document.state', mobile: true },
  { path: '/r/zuerich-stadt', role: null, ready: 'regions.public.hero' },
  { path: '/find-region', role: null },
  { path: '/r/zuerich-stadt/intro-pickups', role: null, ready: 'introPickups.public.state' },
  { path: '/intro-pickups/unsubscribe?token=seed-unsubscribe-quiz', role: null, ready: 'introPickups.unsubscribe' },
  { path: '/newsletter/unsubscribe/not-a-token', role: null, ready: 'newsletters.unsubscribe.page', states: ['notFound', 'error'] },

  // --- dashboard, onboarding, account (M1/M2/M6)
  { path: '/dashboard', role: 'newbie', ready: 'dashboard.page.welcome', mobile: true },
  { path: '/dashboard', role: 'orga', ready: 'dashboard.page.welcome' },
  { path: '/dashboard', role: 'bot', ready: 'dashboard.activity.list' },
  { path: '/onboarding', role: 'newbie', mobile: true },
  { path: '/onboarding/profile', role: 'profile', ready: 'onboarding.profile.form' },
  { path: '/account/settings', role: 'foodsaver', ready: 'account.settings.calendar.url', mobile: true },
  { path: '/account/sessions', role: 'foodsaver', ready: 'account.sessions.list' },
  { path: '/account/security-log', role: 'foodsaver' },
  { path: '/account/profile', role: 'foodsaver' },
  { path: '/account/notifications', role: 'foodsaver', ready: 'notifications.settings.matrix', mobile: true },
  { path: '/me/activity', role: 'foodsaver', mobile: true },
  // M6 account completion (plan-m7 Q3): pending email change of profile@local, email links without/with a bad token
  { path: '/account/settings', role: 'profile', ready: 'account.settings.email.pending' },
  { path: '/account/email/confirm?token=not-a-token', role: 'foodsaver', ready: 'account.email.confirm.tokenError', states: ['error'] },
  { path: '/account/email/abort', role: null, ready: 'account.email.abort.tokenError', states: ['error'] },
  // M6 account completion 2 (plan-m7 Q4): no calendar link yet for guide (foodsaver's link: the settings row above)
  { path: '/account/settings', role: 'guide', ready: 'account.settings.calendar.none' },

  // --- M6 profiles (plan-m7 Q2)
  { path: '/profile/me', role: 'foodsaver', ready: 'profiles.page.name', mobile: true },
  { path: '/profile/me?tab=bananas', role: 'foodsaver', ready: 'profiles.bananas.list' },
  { path: `/profile/${FOODSAVER}?tab=notes`, role: 'orga', ready: 'profiles.notes.composer' },
  { path: `/profile/${SLEEPY}`, role: 'foodsaver', ready: 'profiles.page.sleep' },
  { path: '/profile/me/buddies', role: 'foodsaver', ready: 'profiles.buddies.state' },

  // --- regions and groups (M2)
  { path: '/regions/mine', role: 'foodsaver', ready: 'regions.mine.regions', mobile: true },
  { path: `/regions/${ZH}/overview`, role: 'foodsaver', ready: 'regions.overview.about', mobile: true },
  { path: `/regions/${ZH}/members`, role: 'foodsaver' },
  { path: `/regions/${ZH}/groups`, role: 'foodsaver', ready: 'regions.groups.list' },
  { path: `/regions/${ZH}/statistics`, role: 'foodsaver', ready: 'regions.statistics.genderList' },
  { path: `/regions/${ZH}/options`, role: 'bot' },
  { path: `/regions/${ZH}/pin`, role: 'bot', ready: 'regions.pin.description' },
  { path: `/regions/${ZH}/settings`, role: 'bot' },
  { path: `/regions/${ZH}/history`, role: 'bot' },
  { path: `/regions/${ZH}/wall`, role: 'foodsaver', ready: 'regions.later.empty' },
  { path: `/regions/${ZH}/later/forum`, role: 'foodsaver', ready: 'regions.later.empty' },
  { path: `/groups/${KOCHGRUPPE}`, role: 'foodsaver' },
  { path: '/admin/regions', role: 'orga' },
  { path: `/regions/${ZH}/users`, role: 'bot', ready: 'usersAdmin.list.search' },
  { path: '/regions/home/users', role: 'bot', ready: 'usersAdmin.list.search', mobile: true },
  { path: `/users/${SLEEPY}`, role: 'bot', ready: 'usersAdmin.user.notes.composer' },
  { path: '/admin/bounced-emails', role: 'orga', ready: 'usersAdmin.bounced.state' },

  // --- verification, quizzes, learning, intro pickups (M2)
  { path: `/regions/${ZH}/verification`, role: 'bot', ready: 'verification.queue.state' },
  { path: `/regions/${ZH}/passports`, role: 'bot', ready: 'verification.passports.requestsState' },
  { path: `/users/${FOODSAVER}/verification-history`, role: 'bot', ready: 'verification.history.user' },
  { path: '/me/passport', role: 'foodsaver', ready: 'verification.passport.status', mobile: true },
  { path: '/quizzes', role: 'quiz', ready: 'quiz.list.card', mobile: true },
  { path: '/quizzes/foodsaver', role: 'quiz', ready: 'quiz.intro.description' },
  {
    path: '/quizzes/foodsaver/session',
    // newbie may start the foodsaver quiz (q2-session); the second theme finds the round of the first one running
    role: 'newbie',
    ready: 'quiz.session.question',
    setup: async (api) => {
      const response = await api.fetch('POST', '/api/quizzes/FOODSAVER/sessions', { timed: false });
      if (!response.ok() && response.status() !== 409) throw new Error(`quiz start: ${response.status()} ${await response.text()}`);
    },
  },
  { path: '/quizzes/foodsaver/results', role: 'foodsaver', ready: 'quiz.results.summary' },
  { path: '/quizzes/history', role: 'quiz', ready: 'quiz.history.state' },
  { path: `/quizzes/history/${QUIZ_USER}`, role: 'orga', ready: 'quiz.history.state' },
  { path: '/quizzes/edit', role: 'orga', ready: 'quiz.editor.list' },
  { path: '/quizzes/edit/foodsaver', role: 'orga', ready: 'quiz.editor.settings' },
  { path: '/learn', role: 'foodsaver', ready: 'content.learn.state' },
  { path: '/learn/rules', role: 'foodsaver', ready: 'content.chapter.article' },
  { path: '/intro-pickups', role: 'einab1', ready: 'introPickups.bookable.search' },
  { path: '/me/intro-pickups', role: 'einab1', ready: 'introPickups.my.progress', mobile: true },
  { path: '/guide', role: 'guide', ready: 'introPickups.guide.regionsState', mobile: true },
  { path: '/guide/settings', role: 'guide', ready: 'introPickups.settings.phone' },
  { path: `/regions/${ZH}/newcomers`, role: 'bot', ready: 'introPickups.funnel.total' },
  { path: `/regions/${ZH}/intro-pickups/admin`, role: 'bot', ready: 'introPickups.admin.funnel' },
  { path: `/regions/${ZH}/intro-pickups/import`, role: 'bot', ready: 'introPickups.import.previewButton' },

  // --- legal and content (M2 T14, M6 Q10/Q11)
  { path: '/admin/legal', role: 'orga', ready: 'legal.admin.versions' },
  { path: '/about', role: null, ready: 'content.about.link', mobile: true },
  ...['vision', 'team', 'partners', 'press', 'cities', 'demands', 'transparency', 'education', 'communities', 'international', 'security'].map(
    (page) => ({ path: `/about/${page}`, role: null, ready: 'content.page.article' }),
  ),
  { path: '/imprint', role: null, ready: 'content.page.body', mobile: true },
  { path: '/for-companies', role: null, ready: 'content.page.article' },
  { path: '/pages/vision', role: null, ready: 'content.page.body' },
  { path: '/faq', role: null, ready: 'content.faq.entry', mobile: true },
  { path: '/team', role: null, ready: 'content.team.group', mobile: true },
  { path: '/statistics', role: null, ready: 'content.statistics.tile', mobile: true },
  { path: '/release-notes', role: null, ready: 'content.releaseNotes.note', mobile: true },
  { path: '/petition', role: null, ready: 'content.petition.card' },
  { path: '/donate', role: null, ready: 'content.donate.info', mobile: true },
  { path: '/support', role: null, ready: 'content.support.form', mobile: true },
  { path: '/support?from=%2Fdashboard', role: 'foodsaver', ready: 'content.support.form' },
  { path: '/admin/content', role: 'orga', ready: 'content.admin.page' },
  { path: '/admin/content/press', role: 'orga', ready: 'content.editor.images' },
  { path: '/admin/release-notes', role: 'orga', ready: 'content.releaseAdmin.note' },
  { path: '/blog', role: null, ready: 'blog.list.grid', mobile: true },
  { path: '/blog/00000012-0006-4000-8000-000000000001', role: null, ready: 'blog.post.body' },
  { path: '/blog/manage', role: 'orga', ready: 'blog.manage.new' },
  { path: '/blog/00000012-0006-4000-8000-000000000007/edit', role: 'bot', ready: 'blog.editor.form' },
  { path: '/blog/new', role: 'welcome', ready: 'blog.editor.form' },

  // --- stores, pickups, chains (M3)
  { path: '/stores/mine', role: 'bieb', ready: 'stores.mine.store', mobile: true },
  { path: `/stores/new?regionId=${ZH}`, role: 'bieb', ready: 'stores.form.basics' },
  { path: `/stores/${S01}/edit`, role: 'bieb', ready: 'stores.form.basics' },
  { path: `/stores/${S01}/overview`, role: 'bieb', ready: 'stores.overview.info' },
  { path: `/stores/${S01}/team`, role: 'bieb', ready: 'stores.team.members' },
  { path: `/stores/${S01}/pickups`, role: 'bieb', ready: 'stores.pickups.list' },
  { path: `/stores/${S01}/pickups/history`, role: 'bieb', ready: 'stores.pickupHistory.filter' },
  { path: `/stores/${S01}/wall`, role: 'bieb', ready: 'stores.wall.list' },
  { path: `/stores/${S01}/statistics`, role: 'bieb', ready: 'stores.statistics.totalPickups' },
  { path: `/stores/${S01}/log`, role: 'bieb' },
  { path: `/stores/${S01}/chat`, role: 'bieb', ready: 'stores.chat.channel' },
  ...['policy', 'auto-accept', 'adverts', 'signout-alerts', 'extra-slot', 'audits'].map((section) => ({
    path: `/stores/${S01}/automation/${section}`,
    role: 'bieb' as const,
    ready: 'automation.store.nav',
  })),
  { path: `/regions/${ZH}/stores`, role: 'foodsaver', ready: 'stores.region.filters' },
  { path: `/regions/${ZH}/stores/import`, role: 'orga', ready: 'stores.import.columns' },
  { path: '/pickups', role: 'foodsaver', ready: 'pickups.options.state', mobile: true },
  { path: '/pickups/mine', role: 'foodsaver', ready: 'pickups.mine.state' },
  { path: '/pickups/agenda/2026-11-05', role: 'foodsaver', ready: 'pickups.agenda.day' },
  { path: '/me/pickups/history', role: 'foodsaver', ready: 'pickups.past.state' },
  { path: `/users/${FOODSAVER}/pickups`, role: 'bot', ready: 'pickups.past.state' },
  { path: '/store-chains', role: 'orga', ready: 'storeChains.list.table', mobile: true },
  { path: `/store-chains/${COOP_CHAIN}`, role: 'orga', ready: 'storeChains.chain.details' },
  { path: '/admin/store-categories', role: 'orga' },

  // --- automation (M4)
  ...['overview', 'audits', 'cleaning-exemptions', 'grouping', 'metrics', 'telegram', 'holidays'].map((section) => ({
    path: `/regions/${ZH}/automation/${section}`,
    role: 'bot' as const,
    ready: 'automation.region.nav',
  })),

  // --- notifications, messaging, mailboxes, newsletters, admin ops (M4/M5)
  { path: '/bells', role: 'foodsaver', ready: 'bells.page.item', mobile: true },
  { path: '/messages', role: 'foodsaver', ready: 'messaging.list.item', mobile: true },
  { path: '/messages/new', role: 'foodsaver', ready: 'messaging.list.item' },
  { path: `/messages/${CONVERSATION}`, role: 'foodsaver', ready: 'messaging.conversation.messages' },
  { path: '/mailboxes', role: 'bot', ready: 'mailboxes.rail.mailbox', mobile: true },
  { path: '/mailboxes/new', role: 'orga', ready: 'mailboxes.create.kindRegion' },
  { path: `/mailboxes/${MAILBOX_ZH}/compose`, role: 'bot', ready: 'mailboxes.compose.subject' },
  { path: `/mailboxes/${MAILBOX_ZH}/inbox`, role: 'bot', ready: 'mailboxes.rail.mailbox' },
  { path: `/mailboxes/${MAILBOX_ZH}/inbox/${MAIL_5}`, role: 'bot', ready: 'mailboxes.mail.subject' },
  { path: '/newsletters', role: 'orga', ready: 'newsletters.list.kind', mobile: true },
  { path: `/newsletters/${NEWSLETTER}`, role: 'orga', ready: 'newsletters.editor.root' },
  { path: `/regions/${ZH}/newsletters`, role: 'bot', ready: 'newsletters.list.state' },
  { path: '/admin/integrations', role: 'orga', ready: 'adminOps.integrations.card' },
  { path: '/admin/jobs', role: 'orga', ready: 'adminOps.jobs.table' },
  { path: '/admin/mail-outbox', role: 'orga', ready: 'adminOps.outbox.table' },

  // --- forum, events, polls, surveys (M5)
  { path: `/regions/${ZH}/forum`, role: 'foodsaver', ready: 'forum.list.new' },
  { path: `/regions/${ZH}/forum/new`, role: 'foodsaver', ready: 'forum.new.titleInput' },
  { path: `/regions/${ZH}/forum/ambassadors`, role: 'bot', ready: 'forum.list.new' },
  { path: `/regions/${ZH}/forum/ambassadors/new`, role: 'bot', ready: 'forum.new.titleInput' },
  { path: `/forum/threads/${THREAD}`, role: 'foodsaver', ready: 'forum.thread.state' },
  { path: `/regions/${ZH}/moderation-log`, role: 'bot', ready: 'forum.log.timeline' },
  { path: `/regions/${ZH}/events`, role: 'foodsaver', ready: 'events.list.state' },
  { path: '/events/mine', role: 'foodsaver', ready: 'events.mine.state', mobile: true },
  { path: `/events/new?regionId=${ZH}`, role: 'bot', ready: 'events.form.name' },
  { path: `/events/${EVENT}`, role: 'foodsaver', ready: 'events.event.when' },
  { path: `/events/${EVENT}/edit`, role: 'bot', ready: 'events.form.name' },
  { path: `/regions/${ZH}/polls`, role: 'foodsaver', ready: 'polls.list.state' },
  { path: '/polls/mine', role: 'foodsaver', ready: 'polls.list.state', mobile: true },
  { path: `/polls/new?regionId=${ZH}`, role: 'bot', ready: 'polls.form.name' },
  { path: `/polls/${POLL}`, role: 'foodsaver', ready: 'polls.poll.facts' },
  { path: `/polls/${POLL}/edit`, role: 'bot', ready: 'polls.form.name' },
  { path: '/surveys', role: 'bot', ready: 'surveys.list.item', mobile: true },
  { path: '/surveys/new', role: 'bot', ready: 'surveys.editor.title' },
  { path: `/surveys/${SURVEY}`, role: 'bot', ready: 'surveys.editor.title' },
  { path: `/surveys/${SURVEY}/results`, role: 'bot', ready: 'surveys.results.count' },
  { path: `/survey/${SURVEY}?storeId=${S01}`, role: 'foodsaver', ready: 'surveys.fill.title' },

  // --- M6 food baskets (plan-m7 Q5)
  { path: '/baskets', role: 'foodsaver', ready: 'baskets.nearby.list', mobile: true },
  { path: '/baskets/mine', role: 'foodsaver', ready: 'baskets.mine.offeredList' },
  { path: '/baskets/new', role: 'foodsaver', ready: 'baskets.form' },
  { path: '/baskets/00000048-0001-4000-8000-000000000003', role: 'foodsaver', ready: 'baskets.detail.myRequest' },
  // owner view with requests (bieb's account theme is LIGHT, so the own basket of foodsaver is swept)
  { path: `/baskets/${BASKET_BROT}`, role: 'foodsaver', ready: 'baskets.detail.requests' },
  { path: `/baskets/${BASKET_BROT}/edit`, role: 'foodsaver', ready: 'baskets.form' },
  // --- M6 food share points (plan-m7 Q6): region tab, detail with wall, inactive proposal as FSP admin, form, mine
  { path: `/regions/${ZH}/food-share-points`, role: 'foodsaver', ready: 'foodSharePoints.region.list' },
  { path: `/food-share-points/${FSP_WIEDIKON}`, role: 'foodsaver', ready: 'foodSharePoints.detail.wall.list' },
  { path: '/food-share-points/00000049-0001-4000-8000-000000000005', role: 'bot', ready: 'foodSharePoints.detail.activate' },
  { path: `/food-share-points/new?regionId=${ZH}`, role: 'foodsaver', ready: 'foodSharePoints.form' },
  { path: `/food-share-points/${FSP_WIEDIKON}/edit`, role: 'bieb', ready: 'foodSharePoints.form' },
  { path: '/food-share-points/mine', role: 'foodsaver', ready: 'foodSharePoints.mine.list' },
  // --- M6 reports and mediation (plan-m7 Q8): the report team (reports@local), report 03 has wall posts
  { path: `/regions/${ZH}/reports`, role: 'reports', ready: 'reports.region.list' },
  { path: `/regions/${ZH}/report-log`, role: 'reports', ready: 'reports.log.count' },
  { path: '/reports/00000050-0001-4000-8000-000000000003', role: 'reports', ready: 'reports.detail.wall.list' },
  { path: `/profile/${user(7)}`, role: 'reports', ready: 'reports.user.list' },
  { path: `/regions/${ZH}/mediation`, role: 'foodsaver', ready: 'reports.mediation.team' },
  // --- M6 map (plan-m7 Q7)
  { path: '/map', role: null, ready: 'map.page.layers', mobile: true },
  { path: '/map', role: 'foodsaver', ready: 'map.page.storeFilter' },
  // --- M6 admin settings, uploads, admins (plan-m7 Q13)
  { path: '/admin/feature-toggles', role: 'orga', ready: 'adminSettings.toggles.list', mobile: true },
  { path: '/admin/configuration', role: 'orga', ready: 'adminSettings.configuration.historyButton' },
  { path: '/admin/donations', role: 'orga', ready: 'adminSettings.donations.form' },
  { path: '/admin/email-blocklist', role: 'orga', ready: 'adminSettings.blocklist.table' },
  { path: '/admin/ip-blocks', role: 'orga', ready: 'adminSettings.ipBlocks.addForm' },
  { path: '/admin/uploads', role: 'orga', ready: 'adminSettings.uploads.table' },
  { path: '/admin/admins', role: 'orga', ready: 'admin.admins.count' },
  // --- M6 achievements (plan-m7 Q9): region tab as member and as ambassador, global admin page
  { path: `/regions/${ZH}/achievements`, role: 'foodsaver', ready: 'achievements.board.list' },
  { path: `/regions/${ZH}/achievements`, role: 'bot', ready: 'achievements.board.define' },
  { path: '/admin/achievements', role: 'orga', ready: 'achievements.board.list' },
  // --- M6 search + resources (plan-m7 Q12)
  { path: '/search?q=Wiedikon', role: 'foodsaver', ready: 'search.page.input', mobile: true },
  { path: `/regions/${ZH}/resources`, role: 'foodsaver', ready: 'resources.region.commons' },
  { path: '/resources/mine', role: 'foodsaver', ready: 'resources.mine.list' },
  { path: '/resources/favourites', role: 'foodsaver', ready: 'resources.favourites.list' },
  // --- M6 OAuth (plan-m7 Q14): connections, client administration, consent page with and without a request
  { path: '/account/connections', role: 'foodsaver', ready: 'account.connections.item' },
  { path: '/admin/oauth-clients', role: 'orga', ready: 'oauth.clients.list' },
  { path: '/oauth/consent', role: 'foodsaver', ready: 'oauth.consent.missing' },
  {
    path: '/oauth/consent?request=…',
    role: 'foodsaver',
    ready: 'oauth.consent.approve',
    // a real authorize request of the public seed client (fixed PKCE pair) → the consent page it redirects to
    setup: (api) => consentRequest(api),
  },
];

/**
 * Routes that are not swept, each with its reason. Only routes that never render a page of their own belong here.
 */
export const SWEEP_EXCLUDED: Exclusion[] = [
  { pattern: '/dev', reason: 'redirect to /dev/gallery (swept)' },
  { pattern: '/account', reason: 'redirect to /account/settings' },
  { pattern: '/regions/:regionId', reason: 'redirect to the overview tab' },
  { pattern: '/regions/:regionId/automation', reason: 'redirect to the audits section' },
  { pattern: '/stores/:storeId', reason: 'redirect to the overview tab' },
  { pattern: '/stores/:storeId/automation', reason: 'redirect to the policy section' },
  { pattern: '/content/:slug', reason: 'redirect to the canonical content path' },
  { pattern: '/food-share-points', reason: 'redirect to /food-share-points/mine' },
  { pattern: '/reports', reason: 'redirect to /dashboard' },
  { pattern: '/resources', reason: 'redirect to /resources/mine' },
  { pattern: '/admin/settings', reason: 'redirect to /admin/feature-toggles' },
];
