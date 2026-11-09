import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Drawer } from '@openng/optimus-ui/drawer';

import { TPipe } from '../core/i18n/t.pipe';
import { StartupService } from '../core/startup/startup.service';
import { NavContextService } from './nav-context.service';
import { SidenavSection, sidenavSections } from './nav.config';

/**
 * Phone menu for visitors (below 48rem, not logged in): a "Menu" button in the header that opens a drawer with the
 * same sections as the desktop side menu (Entdecken, Über foodsharing, …), then log in and join. Members use the
 * bottom tab bar and its drawer instead; language and theme stay in the header.
 */
@Component({
  selector: 'fs-visitor-menu',
  imports: [Drawer, RouterLink, RouterLinkActive, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="vm__btn"
      #menuTrigger
      [attr.aria-expanded]="menuOpen()"
      [attr.aria-label]="'nav.menu' | t"
      [attr.title]="'nav.menu' | t"
      (click)="menuOpen.set(true)"
      data-testid="shell.visitorMenu.open"
    >
      <i class="pi pi-bars" aria-hidden="true"></i>
    </button>

    <!-- not part of the start-up bundle (D11.5.3): the drawer's code loads after the first page, it renders on the first tap -->
    @defer (on interaction(menuTrigger); prefetch when startup.pageShown()) {
      <p-drawer
        [visible]="menuOpen()"
        (visibleChange)="menuOpen.set($event)"
        position="right"
        appendTo="body"
        [header]="'nav.menu' | t"
        [ariaCloseLabel]="'common.close' | t"
        styleClass="fs-mobile-menu"
        data-testid="shell.visitorMenu"
      >
        <nav [attr.aria-label]="'layout.header.mainNav' | t">
          @for (section of sections(); track section.def.id) {
            <section class="vm__section" [attr.data-testid]="'shell.visitorMenu.section.' + section.def.id">
              <h2 class="vm__heading">{{ (section.def.visitorLabelKey ?? section.def.labelKey) | t }}</h2>
              <ul class="menu-list">
                @for (item of section.items; track item.id) {
                  <li>
                    <a
                      class="menu-list__item"
                      [routerLink]="item.route"
                      routerLinkActive="menu-list__item--active"
                      [routerLinkActiveOptions]="{ exact: !!item.exact }"
                      #rla="routerLinkActive"
                      [attr.aria-current]="rla.isActive ? 'page' : null"
                      (click)="menuOpen.set(false)"
                      [attr.data-testid]="'shell.visitorMenu.' + item.id"
                    >
                      <i [class]="item.icon" aria-hidden="true"></i>
                      <span>{{ item.labelKey | t }}</span>
                    </a>
                  </li>
                }
              </ul>
            </section>
          }
        </nav>
        <div class="vm__cta">
          <a class="fs-btn" routerLink="/login" (click)="menuOpen.set(false)" data-testid="shell.visitorMenu.login">
            <i class="pi pi-sign-in" aria-hidden="true"></i>
            <span>{{ 'nav.login' | t }}</span>
          </a>
          <a
            class="fs-btn fs-btn--outlined"
            routerLink="/login"
            (click)="menuOpen.set(false)"
            data-testid="shell.visitorMenu.join"
          >
            <i class="pi pi-user-plus" aria-hidden="true"></i>
            <span>{{ 'layout.sidenav.join' | t }}</span>
          </a>
        </div>
      </p-drawer>
    }
  `,
  styles: `
    :host {
      display: contents;
    }
    .vm__btn {
      display: inline-grid;
      width: 2.5rem;
      height: 2.5rem;
      place-items: center;
      border: 0;
      border-radius: var(--fs-radius-md);
      background: none;
      color: var(--fs-text);
      font-size: 1.15rem;
      cursor: pointer;
    }
    .vm__btn:hover,
    .vm__btn:focus-visible {
      background: var(--fs-surface-muted);
    }
    @media (min-width: 48rem) {
      .vm__btn {
        display: none;
      }
    }
    .vm__section + .vm__section {
      margin-top: 1.25rem;
    }
    .vm__heading {
      margin: 0 0 0.5rem;
      color: var(--fs-text-muted);
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }
    .vm__cta {
      display: grid;
      gap: 0.5rem;
      margin-top: 1.5rem;
    }
    .vm__cta .fs-btn {
      justify-content: center;
    }
  `,
})
export class VisitorMenuComponent {
  protected readonly startup = inject(StartupService);
  private readonly nav = inject(NavContextService);

  protected readonly menuOpen = signal(false);
  protected readonly sections = computed<SidenavSection[]>(() => sidenavSections(this.nav.context()));
}
