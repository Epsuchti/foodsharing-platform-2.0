import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideOptimus } from '@openng/optimus-ui/config';

import { Language, Role, Theme } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { provideDataStateTesting } from '../../../core/testing/data-state-testing';
import { PickupHistoryPageComponent } from './pickup-history-page.component';

const ME = 'u-me';
const OTHER = 'u-other';

describe('PickupHistoryPageComponent: delete all future pickups (PU6, decision 164)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideDataStateTesting(), provideOptimus()] });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).applySession({
      authenticated: true,
      permissions: [],
      user: {
        id: ME,
        email: 'orga@local',
        displayName: 'Daniel Huber',
        role: Role.Orga,
        language: Language.De,
        theme: Theme.System,
        verified: true,
      },
    });
  });

  afterEach(() => http.verify());

  async function create(userId: string | undefined) {
    const fixture = TestBed.createComponent(PickupHistoryPageComponent);
    if (userId) {
      fixture.componentRef.setInput('userId', userId);
    }
    await fixture.whenStable();
    const target = userId ?? ME;
    http
      .expectOne((r) => r.url.endsWith(`/api/users/${target}/pickups/past`))
      .flush({ items: [], page: { page: 0, size: 25, totalItems: 0, totalPages: 0 } });
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const button = (el: HTMLElement) => el.querySelector('[data-testid="pickups.past.deleteFuture"]');

  it('has no button on the own history', async () => {
    expect(button(await create(undefined))).toBeNull();
  });

  it('has no button when the route points at oneself', async () => {
    expect(button(await create(ME))).toBeNull();
  });

  it('keeps the admin variant on another user', async () => {
    expect(button(await create(OTHER))).not.toBeNull();
  });
});
