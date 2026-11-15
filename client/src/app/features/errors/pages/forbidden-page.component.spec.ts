import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { Language, Role, Theme } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { ForbiddenPageComponent } from './forbidden-page.component';

/**
 * D4.3: the forbidden page offers anonymous visitors the login and passes the return URL on, but only the app's own
 * paths (`safeReturnUrl`); signed in, it sends people home. Moved here from e2e/tests/shell/d4-error-pages.spec.ts.
 */
describe('ForbiddenPageComponent', () => {
  async function render(returnUrl: string | null, signedIn = false) {
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting(),
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap(returnUrl === null ? {} : { returnUrl })) },
        },
      ],
    });
    if (signedIn) {
      TestBed.inject(SessionService).applySession({
        authenticated: true,
        permissions: [],
        pendingConsents: [],
        user: {
          id: 'u1',
          email: 'a@local',
          displayName: 'Anna',
          role: Role.Foodsaver,
          language: Language.De,
          theme: Theme.System,
          verified: true,
        },
      });
    }
    const fixture = TestBed.createComponent(ForbiddenPageComponent);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    return {
      primary: () => host.querySelector('[data-testid="errorPages.forbidden.primary"]')?.getAttribute('href'),
      back: () => host.querySelector('[data-testid="errorPages.forbidden.back"]'),
    };
  }

  it('offers anonymous visitors the login that comes back to the requested page', async () => {
    const page = await render('/me/activity');
    expect(page.primary()).toBe('/login?returnUrl=%2Fme%2Factivity');
    expect(page.back()).not.toBeNull();
  });

  it('never passes a foreign return URL on', async () => {
    const page = await render('https://evil.example');
    expect(page.primary()).toBe('/login');
  });

  it('sends signed-in people home', async () => {
    const page = await render('/me/activity', true);
    expect(page.primary()).toBe('/dashboard');
    expect(page.back()).not.toBeNull();
  });
});
