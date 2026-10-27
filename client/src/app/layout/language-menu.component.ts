import { ChangeDetectionStrategy, Component, inject, input, viewChild } from '@angular/core';
import { Popover } from '@openng/optimus-ui/popover';

import { I18nService } from '../core/i18n/i18n.service';
import { LANGUAGES, UiLanguage } from '../core/i18n/languages';
import { TPipe } from '../core/i18n/t.pipe';

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
      (click)="op.toggle($event)"
      [attr.aria-label]="'layout.header.language' | t"
      aria-haspopup="true"
      data-testid="shell.languageMenu"
    >
      <i class="pi pi-language" aria-hidden="true"></i>
      <span class="hdr-btn__code">{{
        i18n.language() === 'gws' ? 'CH' : i18n.language().toUpperCase()
      }}</span>
    </button>
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
  `,
})
export class LanguageMenuComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly languages = LANGUAGES;
  protected readonly popover = viewChild.required<Popover>('op');
  readonly showLabel = input(true);

  protected choose(language: UiLanguage): void {
    this.popover().hide();
    void this.i18n.setLanguage(language);
  }
}
