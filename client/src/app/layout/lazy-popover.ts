import { Signal, computed, effect, inject, signal } from '@angular/core';

import { StartupService } from '../core/startup/startup.service';

/** What LazyPopover needs of `p-popover` (kept structural so the Popover class stays out of the start-up bundle). */
export interface PopoverLike {
  toggle(event: Event, target?: unknown): void;
  show(event: Event | null, target?: unknown): void;
  hide(): void;
}

/**
 * Header menus render their `p-popover` inside `@defer (when menu.load())` so the popover code is not
 * part of the start-up bundle (D11.5.3). A click before it has loaded requests it and opens it at the trigger as
 * soon as it is there. Create it in a field initializer (it registers an effect).
 */
export class LazyPopover {
  readonly requested = signal(false);
  private readonly startup = inject(StartupService);
  /** Load after the first page has been shown (StartupService), or at once on a click. */
  readonly load = computed(() => this.startup.pageShown() || this.requested());
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
      this.requested.set(true);
    }
  }

  hide(): void {
    this.popover()?.hide();
  }
}
