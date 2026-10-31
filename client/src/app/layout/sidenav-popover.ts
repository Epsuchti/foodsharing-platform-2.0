import {
  DestroyRef,
  Directive,
  ElementRef,
  Injectable,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';

/** Gap between the side menu's right edge and a floating card (mockup: 12px). */
export const CARD_GAP_PX = 12;
/** Minimum distance of a card to the top and bottom of the viewport. */
export const CARD_MARGIN_PX = 8;

export interface CardPlacement {
  top: number;
  left: number;
  /** Vertical position of the notch inside the card (px from its top), pointing at the middle of the trigger. */
  notch: number;
}

type Box = Pick<DOMRect, 'top' | 'bottom' | 'right' | 'height'>;

/**
 * Where a side menu card goes (decision 133 "Schwebend"): outside the menu's right edge, top aligned with its
 * trigger (bottom aligned for the user block at the bottom), kept inside the viewport.
 */
export function placeCard(
  sidenav: Box,
  trigger: Box,
  cardHeight: number,
  viewportHeight: number,
  anchor: 'top' | 'bottom',
): CardPlacement {
  const wanted = anchor === 'bottom' ? trigger.bottom - cardHeight : trigger.top;
  const top = Math.max(
    CARD_MARGIN_PX,
    Math.min(wanted, viewportHeight - cardHeight - CARD_MARGIN_PX),
  );
  const middle = trigger.top + trigger.height / 2 - top;
  const notch = Math.max(16, Math.min(cardHeight - 16, middle));
  return {
    top: Math.round(top),
    left: Math.round(sidenav.right + CARD_GAP_PX),
    notch: Math.round(notch),
  };
}

/**
 * The one open card of the side menu (user menu, region switcher, bells, visitor preferences, rail flyouts, rail
 * search). Provided by `fs-sidenav`; Escape, a click outside and choosing an item close it (the side menu wires the
 * document events), and closing with `refocus` gives the focus back to the trigger.
 */
@Injectable()
export class SidenavPopovers {
  private readonly openSignal = signal<string | null>(null);
  readonly openId = this.openSignal.asReadonly();
  /** Bumped when the side menu list scrolls or the viewport changes: open cards re-measure. */
  readonly layoutVersion = signal(0);
  private trigger: HTMLElement | null = null;

  get openTrigger(): HTMLElement | null {
    return this.trigger;
  }

  toggle(id: string, trigger: HTMLElement): void {
    if (this.openSignal() === id) {
      this.close(false);
      return;
    }
    this.trigger = trigger;
    this.openSignal.set(id);
  }

  close(refocus: boolean): void {
    if (this.openSignal() === null) return;
    const trigger = this.trigger;
    this.openSignal.set(null);
    this.trigger = null;
    if (refocus && trigger?.isConnected) trigger.focus();
  }

  relayout(): void {
    this.layoutVersion.update((value) => value + 1);
  }
}

/**
 * `<div fsSidenavCard="user" anchor="bottom">` — a raised card of the side menu, fixed next to the menu's right
 * edge while its id is open (hidden otherwise). Focuses its first control when it opens (`focusFirst`, default on).
 */
@Directive({
  selector: '[fsSidenavCard]',
  host: {
    class: 'sn-card',
    '[hidden]': '!open()',
    '[style.top.px]': 'placement()?.top ?? null',
    '[style.left.px]': 'placement()?.left ?? null',
    '[style.--notch-y]': 'notch()',
  },
})
export class SidenavCardDirective {
  private readonly popovers = inject(SidenavPopovers);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);
  readonly id = input.required<string>({ alias: 'fsSidenavCard' });
  readonly anchor = input<'top' | 'bottom'>('top');
  readonly focusFirst = input(true);
  protected readonly open = computed(() => this.popovers.openId() === this.id());
  protected readonly placement = signal<CardPlacement | null>(null);
  protected readonly notch = computed(() => {
    const placement = this.placement();
    return placement ? placement.notch + 'px' : null;
  });

  constructor() {
    const observer =
      typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(() => this.place());
    inject(DestroyRef).onDestroy(() => observer?.disconnect());
    let wasOpen = false;
    effect(() => {
      const open = this.open();
      this.popovers.layoutVersion();
      if (!open) {
        if (wasOpen) observer?.disconnect();
        wasOpen = false;
        return;
      }
      const opening = !wasOpen;
      wasOpen = true;
      afterNextRender(
        () => {
          this.place();
          if (opening) {
            observer?.observe(this.host.nativeElement);
            if (this.focusFirst()) this.focusFirstControl();
          }
        },
        { injector: this.injector },
      );
    });
  }

  private place(): void {
    const card = this.host.nativeElement;
    const trigger = this.popovers.openTrigger;
    const sidenav = card.closest('.sn');
    if (!this.open() || !trigger || !sidenav) return;
    const view = card.ownerDocument.defaultView;
    const next = placeCard(
      sidenav.getBoundingClientRect(),
      trigger.getBoundingClientRect(),
      card.offsetHeight,
      view?.innerHeight ?? 800,
      this.anchor(),
    );
    const current = this.placement();
    if (
      !current ||
      current.top !== next.top ||
      current.left !== next.left ||
      current.notch !== next.notch
    ) {
      this.placement.set(next);
    }
  }

  private focusFirstControl(): void {
    const first = this.host.nativeElement.querySelector<HTMLElement>(
      'input:not([disabled]), a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    first?.focus({ preventScroll: true });
  }
}
