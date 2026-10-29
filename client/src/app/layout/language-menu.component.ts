import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { Popover } from '@openng/optimus-ui/popover';

import { I18nService } from '../core/i18n/i18n.service';
import { LANGUAGES, UiLanguage } from '../core/i18n/languages';
import { TPipe } from '../core/i18n/t.pipe';
import { LazyPopover, PopoverLike } from './lazy-popover';

/** Language switcher (5 languages, names in their own language). */
@Component({
  selector: 'fs-language-menu',
  imports: [Popover, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="hdr-btn"
      [class.hdr-btn--labelled]="showLabel()"
      (click)="menu.toggle($event)"
      aria-haspopup="true"
      data-testid="shell.languageMenu"
    >
      <i class="pi pi-language" aria-hidden="true"></i>
      <span class="fs-visually-hidden">{{ 'layout.header.language' | t }}</span>
      <span class="hdr-btn__code">{{ code() }}</span>
    </button>
    <!-- loaded after the first page or on the first click (LazyPopover, D11.5.3) -->
    @defer (when menu.load()) {
    <p-popover #op>
      <ul class="menu-list" role="menu" [attr.aria-label]="'layout.header.language' | t">
        @for (language of languages; track language) {
          <li role="none">
            <button
              type="button"
              role="menuitemradio"
              class="menu-list__item"
              [class.menu-list__item--active]="language === i18n.language()"
              [attr.aria-checked]="language === i18n.language()"
              [attr.lang]="language === 'gws' ? 'de-CH' : language"
              (click)="choose(language)"
              [attr.data-testid]="'shell.language.' + language"
            >
              <span>{{ 'lang.' + language | t }}</span>
              @if (language === i18n.language()) {
                <i class="pi pi-check" aria-hidden="true"></i>
              }
            </button>
          </li>
        }
      </ul>
    </p-popover>
    }
  `,
})
export class LanguageMenuComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly languages = LANGUAGES;
  private readonly popover = viewChild<PopoverLike>('op');
  protected readonly menu = new LazyPopover(this.popover);
  readonly showLabel = input(true);
  /** Short code shown on the button (and part of its accessible name, WCAG 2.5.3). */
  protected readonly code = computed(() =>
    this.i18n.language() === 'gws' ? 'CH' : this.i18n.language().toUpperCase(),
  );

  protected choose(language: UiLanguage): void {
    this.menu.hide();
    void this.i18n.setLanguage(language);
  }
}
