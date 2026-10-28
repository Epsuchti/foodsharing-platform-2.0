import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';
import { afterEach } from 'vitest';

import { Language, Role, Theme, UserProfile } from '../../../api';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { ProfilePageComponent } from './profile-page.component';

const PROFILE: UserProfile = {
  id: '00000001-0001-4000-8000-000000000006',
  email: 'foodsaver@local',
  displayName: 'Sarah Müller',
  language: Language.De,
  theme: Theme.System,
  role: Role.Foodsaver,
  verified: true,
  registeredAt: '2025-12-19T18:18:00Z',
};

describe('ProfilePageComponent', () => {
  let fixture: ComponentFixture<ProfilePageComponent>;
  let http: HttpTestingController;
  let host: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProfilePageComponent);
    host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  const state = () => host.querySelector('[data-testid="account.profile.state"]')!;

  it('shows a skeleton, then the profile', async () => {
    expect(state().getAttribute('data-state')).toBe('loading');
    http.expectOne('/api/users/me').flush(PROFILE);
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="account.profile.name"]')?.textContent).toBe(
      'Sarah Müller',
    );
  });

  it('shows the error with retry and recovers', async () => {
    http
      .expectOne('/api/users/me')
      .flush(
        { code: 'UNEXPECTED_ERROR', message: 'x', traceId: 't', retryable: true },
        { status: 503, statusText: 'Unavailable' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('error');
    (
      host.querySelector('[data-testid="account.profile.state.retry"] button') as HTMLElement
    ).click();
    http.expectOne('/api/users/me').flush(PROFILE);
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('success');
  });

  it('shows not found when the account is gone', async () => {
    http
      .expectOne('/api/users/me')
      .flush(
        { code: 'USER_NOT_FOUND', message: 'x', traceId: 't', retryable: false },
        { status: 404, statusText: 'Not Found' },
      );
    await fixture.whenStable();
    expect(state().getAttribute('data-state')).toBe('notFound');
  });
  it('saves edited fields; cleared fields are sent empty, the birthday omitted when empty', async () => {
    http.expectOne('/api/users/me').flush({ ...PROFILE, city: 'Zürich', mobilePhone: '+41791234567' });
    await fixture.whenStable();
    const save = () => host.querySelector('[data-testid="account.profile.save"] button') as HTMLButtonElement;
    expect(save().disabled).toBe(true);

    const city = host.querySelector('[data-testid="account.profile.city"]') as HTMLInputElement;
    city.value = '';
    city.dispatchEvent(new Event('input'));
    const street = host.querySelector('[data-testid="account.profile.street"]') as HTMLInputElement;
    street.value = 'Langstrasse 1';
    street.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    save().click();

    const request = http.expectOne('/api/users/me/profile');
    expect(request.request.body).toMatchObject({
      displayName: 'Sarah Müller',
      street: 'Langstrasse 1',
      city: '',
      mobilePhone: '+41791234567',
    });
    expect(request.request.body.dateOfBirth).toBeUndefined();
    request.flush({ ...PROFILE, street: 'Langstrasse 1' });
    await fixture.whenStable();
    expect(save().disabled).toBe(true);
  });
});
