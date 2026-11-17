import { Route } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';
import { ADMIN_OPS_ROUTES } from '../admin-ops/admin-ops.routes';
import { CONTENT_ROUTES } from '../content/content.routes';
import { OAUTH_ROUTES } from '../oauth/oauth.routes';
import { ADMIN_SETTINGS_ROUTES } from './admin-settings.routes';

/**
 * C10, C11, C12, C14, AD1, D6.3, AU12, N6, C4, C13: the ORGA pages (settings, uploads, admins, operations, release
 * note editor, OAuth clients) are guarded for ORGA only, so everybody else lands on /forbidden (authGuard, see
 * auth.guard.spec). The content editor is not on this list: the server decides per page function (C1).
 */
function orgaOnly(route: Route): boolean {
  return !!route.canActivate?.includes(authGuard) && JSON.stringify(route.data?.['roles']) === JSON.stringify([Role.Orga]);
}

describe('ORGA routes', () => {
  it('guard every settings page, the uploads and the admins list', () => {
    const pages = ADMIN_SETTINGS_ROUTES.filter((route) => !route.redirectTo);
    expect(pages.map((route) => route.path)).toEqual(
      expect.arrayContaining([
        'admin/feature-toggles',
        'admin/configuration',
        'admin/donations',
        'admin/email-blocklist',
        'admin/ip-blocks',
        'admin/uploads',
        'admin/admins',
      ]),
    );
    for (const route of pages) expect(orgaOnly(route), route.path).toBe(true);
  });

  it('guard the operations pages', () => {
    expect(ADMIN_OPS_ROUTES.map((route) => route.path)).toEqual(['integrations', 'jobs', 'mail-outbox']);
    for (const route of ADMIN_OPS_ROUTES) expect(orgaOnly(route), route.path).toBe(true);
  });

  it('guard the release note editor and the OAuth clients', () => {
    const releaseNotes = CONTENT_ROUTES.find((route) => route.path === 'admin/release-notes');
    const clients = OAUTH_ROUTES.find((route) => route.path === 'admin/oauth-clients');
    expect(releaseNotes && orgaOnly(releaseNotes)).toBe(true);
    expect(clients && orgaOnly(clients)).toBe(true);
  });
});
