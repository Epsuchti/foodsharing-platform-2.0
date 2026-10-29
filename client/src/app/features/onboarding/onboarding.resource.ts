import { Injectable, inject } from '@angular/core';
import { NavigationStart, Router } from '@angular/router';
import { Observable, filter, of, share, tap } from 'rxjs';

import { Journey, OnboardingService } from '../../api';

/**
 * `GET /api/onboarding/journey` shared between the journey bar in the header and the page below it (`/onboarding`,
 * dashboard): both ask for the journey while a page renders, and one request is enough — the page then renders
 * without waiting for a second round trip (D11.5.3, Lighthouse LCP on `/onboarding`). A request in flight is
 * shared; its result is reused until the next navigation starts (a step may have changed in between).
 */
@Injectable({ providedIn: 'root' })
export class JourneyLoader {
  private readonly api = inject(OnboardingService);
  private inFlight?: Observable<Journey>;
  private last?: Journey;

  constructor() {
    inject(Router)
      .events.pipe(filter((event) => event instanceof NavigationStart))
      .subscribe(() => (this.last = undefined));
  }

  load(): Observable<Journey> {
    if (this.last) {
      return of(this.last);
    }
    this.inFlight ??= this.api.getMyJourney().pipe(
      tap({
        next: (journey) => (this.last = journey),
        finalize: () => (this.inFlight = undefined),
      }),
      share(),
    );
    return this.inFlight;
  }
}
