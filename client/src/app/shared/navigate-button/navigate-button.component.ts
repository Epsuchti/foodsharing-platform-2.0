import { ChangeDetectionStrategy, Component, ElementRef, input, signal, viewChild } from '@angular/core';
import { ButtonDirective, ButtonIcon, ButtonLabel } from '@openng/optimus-ui/button';
import { Popover } from '@openng/optimus-ui/popover';

import { TPipe } from '../../core/i18n/t.pipe';
import { NavigationLinks } from './navigate.util';

/**
 * "Navigate" button with a dropdown of directions links (Google Maps, Apple Maps; resume item 15). The popover
 * renders to the body, so tests find the items by test ID (`<testId>.google`, `<testId>.apple`).
 * `<fs-navigate-button [links]="nav" testId="stores.page.navigate" />`
 */
@Component({
  selector: 'fs-navigate-button',
  imports: [ButtonDirective, ButtonIcon, ButtonLabel, Popover, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-navigate-button' },
  template: `
    <!-- a real pButton (review fix A8): only an instantiated Button injects the button styles, so plain p-button
         classes stay unstyled on pages without another button (store pickups on phones) -->
    <button
      #trigger
      pButton
      type="button"
      severity="secondary"
      [outlined]="true"
      [size]="small() ? 'small' : undefined"
      class="trigger"
      aria-haspopup="menu"
      [attr.aria-expanded]="open()"
      (click)="menu.toggle($event)"
      [attr.data-testid]="testId()"
    >
      <i pButtonIcon class="pi pi-directions" aria-hidden="true"></i>
      <span pButtonLabel>{{ 'shared.navigate.button' | t }}</span>
      <i class="pi pi-chevron-down trigger__chevron" aria-hidden="true"></i>
    </button>
    <p-popover #menu (onShow)="open.set(true)" (onHide)="hidden()" [attr.data-testid]="testId() + 'Panel'">
      <div class="menu">
        @if (note(); as text) {
          <p class="menu__note" [attr.data-testid]="testId() + '.note'">
            <i class="pi pi-info-circle" aria-hidden="true"></i>{{ text }}
          </p>
        }
        <ul class="menu__list" role="menu" [attr.aria-label]="'shared.navigate.button' | t">
          <li role="none">
            <a
              role="menuitem"
              class="menu__item"
              [href]="links().google"
              target="_blank"
              rel="noopener noreferrer"
              (click)="menu.hide()"
              [attr.data-testid]="testId() + '.google'"
            >
              <i class="pi pi-map" aria-hidden="true"></i>{{ 'shared.navigate.google' | t }}
              <span class="fs-visually-hidden">{{ 'shared.navigate.newTab' | t }}</span>
            </a>
          </li>
          <li role="none">
            <a
              role="menuitem"
              class="menu__item"
              [href]="links().apple"
              target="_blank"
              rel="noopener noreferrer"
              (click)="menu.hide()"
              [attr.data-testid]="testId() + '.apple'"
            >
              <i class="pi pi-apple" aria-hidden="true"></i>{{ 'shared.navigate.apple' | t }}
              <span class="fs-visually-hidden">{{ 'shared.navigate.newTab' | t }}</span>
            </a>
          </li>
        </ul>
      </div>
    </p-popover>
  `,
  styles: `
    :host {
      display: inline-flex;
      justify-self: start;
    }
    .trigger {
      display: inline-flex;
      gap: 0.4rem;
      align-items: center;
      min-height: var(--fs-target-sm);
    }
    .trigger__chevron {
      font-size: 0.7rem;
      transition: transform 150ms ease;
    }
    .trigger[aria-expanded='true'] .trigger__chevron {
      transform: rotate(180deg);
    }
    .menu {
      display: grid;
      gap: 0.25rem;
      min-width: 14rem;
      max-width: min(20rem, calc(100vw - 2rem));
    }
    .menu__note {
      display: flex;
      gap: 0.5rem;
      align-items: flex-start;
      margin: 0;
      padding: 0.4rem 0.75rem;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .menu__list {
      display: grid;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .menu__item {
      display: flex;
      gap: 0.6rem;
      align-items: center;
      min-height: var(--fs-target-sm);
      padding: 0.55rem 0.75rem;
      border-radius: 0.4rem;
      color: inherit;
      text-decoration: none;
    }
    .menu__item:hover,
    .menu__item:focus-visible {
      background: var(--fs-surface-muted);
    }
  `,
})
export class NavigateButtonComponent {
  readonly links = input.required<NavigationLinks>();
  /** Trigger test ID; the items get `.google` and `.apple`, the note `.note`. */
  readonly testId = input.required<string>();
  /** Already translated hint shown above the items (e.g. an approximate location). */
  readonly note = input<string | null | undefined>(undefined);
  readonly small = input(false);

  protected readonly open = signal(false);
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');

  /** Closed by Escape, an item or a click outside: focus goes back to the trigger unless it moved elsewhere. */
  protected hidden(): void {
    this.open.set(false);
    const active = document.activeElement;
    if (!active || active === document.body || active.closest('.p-popover')) {
      this.trigger().nativeElement.focus();
    }
  }
}
