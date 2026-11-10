import { Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { OnlineStatusService } from '../core/online/online-status.service';
import { StartupService } from '../core/startup/startup.service';
import { TestingOnlineStatusService } from '../core/testing/data-state-testing';
import { LazyPopover, PopoverLike } from './lazy-popover';

describe('LazyPopover', () => {
  let online: TestingOnlineStatusService;
  let startup: { pageShown: ReturnType<typeof signal<boolean>> };
  let menu: LazyPopover;

  beforeEach(() => {
    online = new TestingOnlineStatusService();
    startup = { pageShown: signal(false) };
    TestBed.configureTestingModule({
      providers: [
        { provide: OnlineStatusService, useValue: online },
        { provide: StartupService, useValue: startup },
      ],
    });
    runInInjectionContext(TestBed.inject(Injector), () => {
      menu = new LazyPopover(signal<PopoverLike | undefined>(undefined));
    });
  });

  it('prefetches the popover code only after the first page and while online (offline NG0750 toast)', () => {
    expect(menu.prefetch()).toBe(false);
    startup.pageShown.set(true);
    expect(menu.prefetch()).toBe(true);
    online.setOnline(false);
    expect(menu.prefetch()).toBe(false);
    online.setOnline(true);
    expect(menu.prefetch()).toBe(true);
  });
});
