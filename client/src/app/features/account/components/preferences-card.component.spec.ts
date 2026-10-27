import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach, vi } from 'vitest';

import { SessionService } from '../../../core/auth/session.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { ThemeService } from '../../../core/theme/theme.service';
import { sessionInfo, settle } from '../../auth/testing';
import { PreferencesCardComponent } from './preferences-card.component';

describe('PreferencesCardComponent', () => {
  let fixture: ComponentFixture<PreferencesCardComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;
  let i18n: I18nService;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    i18n = TestBed.inject(I18nService);
    // catalogs come from memory: switching languages must not hit /i18n/<lang>.json
    for (const language of ['fr', 'de'] as const) {
      i18n.useCatalog({}, language);
    }
    TestBed.inject(SessionService).applySession(sessionInfo());
    TestBed.inject(ThemeService).setPreference('system', { persist: false });
    fixture = TestBed.createComponent(PreferencesCardComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const click = (id: string) =>
    (host.querySelector(`[data-testid="${id}"]`) as HTMLButtonElement).click();

  it('marks the current language and theme', () => {
    expect(
      host
        .querySelector('[data-testid="account.settings.language.de"]')
        ?.getAttribute('aria-checked'),
    ).toBe('true');
    expect(
      host
        .querySelector('[data-testid="account.settings.theme.system"]')
        ?.getAttribute('aria-checked'),
    ).toBe('true');
  });

  it('switches the language right away and saves it to the account', async () => {
    click('account.settings.language.fr');
    await vi
      .waitFor(() => http.expectOne('/api/users/me/settings'), { timeout: 1000 })
      .then((request) => {
        expect(request.request.body).toEqual({ language: 'fr' });
        request.flush({});
      });
    await settle(fixture);
    expect(i18n.language()).toBe('fr');
    expect(TestBed.inject(SessionService).user()?.language).toBe('fr');
  });

  it('rolls the theme back when saving fails', async () => {
    const theme = TestBed.inject(ThemeService);
    click('account.settings.theme.dark');
    expect(theme.preference()).toBe('dark');
    http
      .expectOne('/api/users/me/settings')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true },
        { status: 500, statusText: 'Error' },
      );
    await settle(fixture);
    expect(theme.preference()).toBe('system');
  });
});
