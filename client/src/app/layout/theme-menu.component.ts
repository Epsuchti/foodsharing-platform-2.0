import { ChangeDetectionStrategy, Component, computed, inject, viewChild } from '@angular/core';
import { Popover } from '@openng/optimus-ui/popover';

import { TPipe } from '../core/i18n/t.pipe';
import { THEME_PREFERENCES, ThemePreference, ThemeService } from '../core/theme/theme.service';

const ICONS: Record<ThemePreference, string> = {
  light: 'pi pi-sun',
  dark: 'pi pi-moon',
  system: 'pi pi-desktop',
};

/** Light / dark / system switcher. */
@Component({
  selector: 'fs-theme-menu',
  imports: [Popover, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="hdr-btn"
      (click)="op.toggle($event)"
      [attr.aria-label]="'layout.header.theme' | t"
      aria-haspopup="true"
      data-testid="shell.themeMenu"
    >
      <i [class]="icon()" aria-hidden="true"></i>
    </button>
    <p-popover #op>
      <ul class="menu-list" role="menu" [attr.aria-label]="'layout.header.theme' | t">
        @for (mode of modes; track mode) {
          <li role="none">
            <button
              type="button"
              role="menuitemradio"
              class="menu-list__item"
              [class.menu-list__item--active]="mode === theme.preference()"
              [attr.aria-checked]="mode === theme.preference()"
              (click)="choose(mode)"
              [attr.data-testid]="'shell.theme.' + mode"
            >
              <i [class]="icons[mode]" aria-hidden="true"></i>
              <span>{{ 'theme.' + mode | t }}</span>
              @if (mode === theme.preference()) {
                <i class="pi pi-check" aria-hidden="true"></i>
              }
            </button>
          </li>
        }
      </ul>
    </p-popover>
  `,
})
export class ThemeMenuComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly modes = THEME_PREFERENCES;
  protected readonly icons = ICONS;
  protected readonly popover = viewChild.required<Popover>('op');
  protected readonly icon = computed(() => (this.theme.isDark() ? ICONS.dark : ICONS.light));

  protected choose(mode: ThemePreference): void {
    this.popover().hide();
    this.theme.setPreference(mode);
  }
}
