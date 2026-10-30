import { Signal, effect, inject } from '@angular/core';

import { StartupService } from '../core/startup/startup.service';

/** What LazyPopover needs of `p-popover` (kept structural so the Popover class stays out of the start-up bundle). */
export interface PopoverLike {
  toggle(event: Event, target?: unknown): void;
  show(event: Event | null, target?: unknown): void;
  hide(): void;
}

/**
 * Header menus render their `p-popover` inside `@defer (on interaction(menuTrigger); prefetch when menu.prefetch())`
 * so the popover code is not part of the start-up bundle (D11.5.3). The code is fetched after the first page; the
 * popover renders on the first click of its button and opens at the trigger as soon as it is there (rendering every
 * header menu right after the first page was part of one long task). Create it in a field initializer (it registers
 * an effect).
 */
export class LazyPopover {
  /** Fetch the code after the first page has been shown (StartupService). */
  readonly prefetch = inject(StartupService).pageShown;
  private pending: EventTarget | null = null;

  constructor(private readonly popover: Signal<PopoverLike | undefined>) {
    effect(() => {
      const loaded = this.popover();
      if (loaded && this.pending) {
        const target = this.pending;
        this.pending = null;
        loaded.show(null, target);
      }
    });
  }

  toggle(event: Event): void {
    const loaded = this.popover();
    if (loaded) {
      loaded.toggle(event);
    } else {
      this.pending = event.currentTarget;
    }
  }

  hide(): void {
    this.popover()?.hide();
  }
}
