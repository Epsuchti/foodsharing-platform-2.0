import { inject } from '@angular/core';
import { Observable } from 'rxjs';

import { IntroPickupsService } from '../../api';

/**
 * Lazy downloads handed to `fs-calendar-export-button` (it calls the function on click, not on render).
 * Exempt from the DataState rule as a `*.resource.ts` file.
 */
export function introPickupIcs(): (introPickupId: string) => () => Observable<string> {
  const api = inject(IntroPickupsService);
  // the generator prefers application/json when both are listed; ask for the calendar explicitly
  return (introPickupId) => () =>
    api.getIntroPickupIcs({ introPickupId }, 'body', false, { httpHeaderAccept: 'text/calendar' });
}
