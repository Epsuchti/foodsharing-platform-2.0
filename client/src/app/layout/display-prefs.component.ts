import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';

import { I18nService } from '../core/i18n/i18n.service';
import { LANGUAGES, UiLanguage } from '../core/i18n/languages';
import { TPipe } from '../core/i18n/t.pipe';
import { THEME_PREFERENCES, ThemePreference, ThemeService } from '../core/theme/theme.service';

/**
 * Language and theme chips of the desktop side menu (decision 133): inside the user menu as two rows that expand
 * ("Sprache · Deutsch", "Darstellung · Hell"), in the visitors' "Sprache & Darstellung" card as two headed groups.
 * Test ids: `<testIdPrefix>.language.<lang>`, `<testIdPrefix>.theme.<mode>`.
 */
@Component({
  selector: 'fs-display-prefs',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: contents' },
  template: `
    @if (disclosure()) {
      <button
        type="button"
        class="sn-row"
        [attr.aria-expanded]="languageOpen()"
        [attr.aria-controls]="testIdPrefix() + '-language'"
        (click)="languageOpen.set(!languageOpen())"
        [attr.data-testid]="testIdPrefix() + '.language'"
      >
        <i class="pi pi-language" aria-hidden="true"></i>
        <span class="sn-row__label">
          {{ 'layout.header.language' | t }}
          <span class="sn-row__small">{{ 'lang.' + i18n.language() | t }}</span>
        </span>
        <i class="pi pi-chevron-down sn-row__chev" aria-hidden="true"></i>
      </button>
    } @else {
      <h3 class="sn-card__h">{{ 'layout.header.language' | t }}</h3>
    }
    <div
      class="sn-chips"
      role="group"
      [id]="testIdPrefix() + '-language'"
      [attr.aria-label]="'layout.header.language' | t"
      [hidden]="disclosure() && !languageOpen()"
    >
      @for (language of languages; track language) {
        <button
          type="button"
          class="sn-chip"
          [attr.aria-pressed]="language === i18n.language()"
          [attr.lang]="language === 'gws' ? 'de-CH' : language"
          (click)="setLanguage(language)"
          [attr.data-testid]="testIdPrefix() + '.language.' + language"
        >
          {{ 'lang.' + language | t }}
        </button>
      }
    </div>
    @if (disclosure()) {
      <button
        type="button"
        class="sn-row"
        [attr.aria-expanded]="themeOpen()"
        [attr.aria-controls]="testIdPrefix() + '-theme'"
        (click)="themeOpen.set(!themeOpen())"
        [attr.data-testid]="testIdPrefix() + '.theme'"
      >
        <i [class]="theme.isDark() ? 'pi pi-moon' : 'pi pi-sun'" aria-hidden="true"></i>
        <span class="sn-row__label">
          {{ 'layout.header.theme' | t }}
          <span class="sn-row__small">{{ 'theme.' + theme.preference() | t }}</span>
        </span>
        <i class="pi pi-chevron-down sn-row__chev" aria-hidden="true"></i>
      </button>
    } @else {
      <h3 class="sn-card__h">{{ 'layout.header.theme' | t }}</h3>
    }
    <div
      class="sn-chips"
      role="group"
      [id]="testIdPrefix() + '-theme'"
      [attr.aria-label]="'layout.header.theme' | t"
      [hidden]="disclosure() && !themeOpen()"
    >
      @for (mode of themes; track mode) {
        <button
          type="button"
          class="sn-chip"
          [attr.aria-pressed]="mode === theme.preference()"
          (click)="theme.setPreference(mode)"
          [attr.data-testid]="testIdPrefix() + '.theme.' + mode"
        >
          {{ 'theme.' + mode | t }}
        </button>
      }
    </div>
  `,
})
export class DisplayPrefsComponent {
  protected readonly i18n = inject(I18nService);
  protected readonly theme = inject(ThemeService);
  protected readonly languages = LANGUAGES;
  protected readonly themes: readonly ThemePreference[] = THEME_PREFERENCES;
  readonly testIdPrefix = input.required<string>();
  /** Rows that expand to the chips (user menu) instead of headed chip groups (visitors). */
  readonly disclosure = input(false);
  protected readonly languageOpen = signal(false);
  protected readonly themeOpen = signal(false);

  protected setLanguage(language: UiLanguage): void {
    void this.i18n.setLanguage(language);
  }
}
