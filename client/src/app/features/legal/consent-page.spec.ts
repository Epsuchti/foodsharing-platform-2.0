import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { of } from 'rxjs';
import { afterEach, vi } from 'vitest';

import { AuthFacadeService } from '../../core/auth/auth-facade.service';
import { provideDataStateTesting } from '../../core/testing/data-state-testing';
import { ConsentPageComponent } from './pages/consent-page.component';

describe('legal consent page (EA4)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ConsentPageComponent>;
  const logout = vi.fn(() => of(null));
  const q = (testId: string) => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const settle = () => fixture.whenStable();

  beforeEach(() => {
    logout.mockClear();
    TestBed.configureTestingModule({
      providers: [
        ...provideDataStateTesting({ translations: { 'nav.logout': 'Abmelden' } }),
        provideOptimus(),
        { provide: AuthFacadeService, useValue: { logout } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ConsentPageComponent);
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  it('offers the user-menu logout on the consent wall (minimal chrome, decision 175)', async () => {
    await settle();
    http.expectOne('/api/legal/consents').flush([]);
    await settle();
    const button = q('legal.consent.logout')?.querySelector('button') as HTMLButtonElement;
    expect(button.textContent?.trim()).toBe('Abmelden');
    button.click();
    await settle();
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('keeps the logout button when the documents fail to load', async () => {
    await settle();
    http.expectOne('/api/legal/consents').flush(null, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(q('legal.consent.documents')?.getAttribute('data-state')).toBe('error');
    expect(q('legal.consent.logout')?.querySelector('button')).not.toBeNull();
  });
});
