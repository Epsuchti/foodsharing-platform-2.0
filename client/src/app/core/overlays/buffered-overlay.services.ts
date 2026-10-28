import { Provider } from '@angular/core';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { ReplaySubject } from 'rxjs';

/** How long a toast / confirmation raised before the overlays exist is kept for them. */
const REPLAY_MS = 15_000;

/**
 * `p-toast` and `p-confirmdialog` load after the first page has rendered (ShellComponent, D11.5.3). The stock
 * services emit through plain (private) Subjects, so a toast raised before that would be lost; these providers
 * swap in replaying subjects so the overlay gets recent ones when it subscribes.
 */
export function provideBufferedOverlayServices(): Provider[] {
  return [
    {
      provide: MessageService,
      useFactory: () => {
        const service = new MessageService();
        const source = new ReplaySubject<unknown>(10, REPLAY_MS);
        Object.assign(service, { messageSource: source, messageObserver: source.asObservable() });
        return service;
      },
    },
    {
      provide: ConfirmationService,
      useFactory: () => {
        const service = new ConfirmationService();
        const source = new ReplaySubject<unknown>(1, REPLAY_MS);
        Object.assign(service, {
          requireConfirmationSource: source,
          requireConfirmation$: source.asObservable(),
        });
        return service;
      },
    },
  ];
}
