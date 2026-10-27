import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Language, UserSettingsUpdate, UsersService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { LANGUAGES, UiLanguage } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import {
  THEME_PREFERENCES,
  ThemePreference,
  ThemeService,
  themeToApi,
} from '../../../core/theme/theme.service';

const THEME_ICONS: Record<ThemePreference, string> = {
  light: 'pi pi-sun',
  dark: 'pi pi-moon',
  system: 'pi pi-desktop',
};

/**
 * Language (A12) and theme (A17), saved as account settings (PATCH /api/users/me/settings). The change applies
 * immediately and is rolled back when saving fails.
 */
@Component({
  selector: 'fs-preferences-card',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section
      class="fs-card card"
      aria-labelledby="prefs-title"
      data-testid="account.settings.preferences"
    >
      <header class="card__head">
        <span class="card__icon" aria-hidden="true"><i class="pi pi-palette"></i></span>
        <div>
          <h2 id="prefs-title">{{ 'account.settings.preferencesTitle' | t }}</h2>
          <p>{{ 'account.settings.preferencesText' | t }}</p>
        </div>
      </header>

      <div class="group">
        <h3 id="lang-label" class="group__label">
          <i class="pi pi-language" aria-hidden="true"></i>
          {{ 'account.settings.language' | t }}
        </h3>
        <div class="options options--lang" role="radiogroup" aria-labelledby="lang-label">
          @for (language of languages; track language) {
            <button
              type="button"
              role="radio"
              class="option"
              [class.option--active]="language === i18n.language()"
              [attr.aria-checked]="language === i18n.language()"
              [attr.lang]="language === 'gws' ? 'de-CH' : language"
              [disabled]="save.busy()"
              (click)="chooseLanguage(language)"
              [attr.data-testid]="'account.settings.language.' + language"
            >
              <span class="option__code" aria-hidden="true">{{
                language === 'gws' ? 'CH' : language.toUpperCase()
              }}</span>
              <span class="option__label">{{ 'lang.' + language | t }}</span>
              @if (language === i18n.language()) {
                <i class="pi pi-check-circle option__check" aria-hidden="true"></i>
              }
            </button>
          }
        </div>
      </div>

      <div class="group">
        <h3 id="theme-label" class="group__label">
          <i class="pi pi-sun" aria-hidden="true"></i>
          {{ 'account.settings.theme' | t }}
        </h3>
        <div class="options options--theme" role="radiogroup" aria-labelledby="theme-label">
          @for (mode of themes; track mode) {
            <button
              type="button"
              role="radio"
              class="option option--theme"
              [class.option--active]="mode === theme.preference()"
              [attr.aria-checked]="mode === theme.preference()"
              [disabled]="save.busy()"
              (click)="chooseTheme(mode)"
              [attr.data-testid]="'account.settings.theme.' + mode"
            >
              <span class="swatch" [attr.data-mode]="mode" aria-hidden="true">
                <i [class]="themeIcons[mode]"></i>
              </span>
              <span class="option__label">{{ 'theme.' + mode | t }}</span>
            </button>
          }
        </div>
      </div>
    </section>
  `,
  styleUrl: './account-card.scss',
  styles: `
    .group {
      display: grid;
      gap: 0.65rem;
    }
    .group + .group {
      padding-top: 1.1rem;
      border-top: 1px solid var(--fs-border);
    }
    .group__label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-size: 0.95rem;
      font-weight: 700;
    }
    .group__label .pi {
      color: var(--fs-text-muted);
    }
    .options {
      display: grid;
      gap: 0.5rem;
    }
    .options--lang {
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 10rem), 1fr));
    }
    .options--theme {
      grid-template-columns: repeat(3, minmax(0, 1fr));
    }
    .option {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      min-height: 3rem;
      padding: 0.55rem 0.8rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
      color: var(--fs-text);
      font: inherit;
      font-weight: 600;
      text-align: left;
      cursor: pointer;
      transition:
        border-color 150ms ease,
        background-color 150ms ease;
    }
    .option:hover:not(:disabled) {
      border-color: var(--fs-border-strong);
      background: var(--fs-surface-muted);
    }
    .option:disabled {
      cursor: progress;
    }
    .option--active,
    .option--active:hover:not(:disabled) {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .option__code {
      display: grid;
      min-width: 2rem;
      height: 1.6rem;
      place-items: center;
      border-radius: 0.4rem;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.04em;
    }
    .option--active .option__code {
      background: var(--fs-primary);
      color: #fff;
    }
    :host-context(.fs-dark) .option--active .option__code {
      background: #25834a;
    }
    .option__label {
      flex: 1 1 auto;
      min-width: 0;
    }
    .option__check {
      color: var(--fs-primary);
    }
    .option--theme {
      flex-direction: column;
      justify-content: center;
      gap: 0.45rem;
      padding: 0.8rem 0.5rem;
      text-align: center;
    }
    .option--theme .option__label {
      font-size: 0.88rem;
    }
    .swatch {
      display: grid;
      width: 2.4rem;
      height: 2.4rem;
      place-items: center;
      border: 1px solid var(--fs-border-strong);
      border-radius: 50%;
      font-size: 1rem;
    }
    .swatch[data-mode='light'] {
      background: #fff;
      color: #a35f0c;
    }
    .swatch[data-mode='dark'] {
      background: #1a201a;
      color: #f6d79b;
    }
    .swatch[data-mode='system'] {
      background: linear-gradient(135deg, #fff 0 50%, #1a201a 50% 100%);
      color: #25834a;
    }
    .swatch[data-mode='system'] .pi {
      padding: 0.25rem;
      border-radius: 50%;
      background: #fff;
    }
  `,
})
export class PreferencesCardComponent {
  private readonly usersApi = inject(UsersService);
  private readonly session = inject(SessionService);
  protected readonly i18n = inject(I18nService);
  protected readonly theme = inject(ThemeService);

  protected readonly languages = LANGUAGES;
  protected readonly themes = THEME_PREFERENCES;
  protected readonly themeIcons = THEME_ICONS;

  protected readonly save = runMutation(
    (update: UserSettingsUpdate) => this.usersApi.updateMySettings({ userSettingsUpdate: update }),
    { successToastKey: 'account.settings.saved' },
  );

  protected async chooseLanguage(language: UiLanguage): Promise<void> {
    const previous = this.i18n.language();
    if (language === previous) {
      return;
    }
    await this.i18n.setLanguage(language, { persist: false });
    this.session.patchUser({ language: language as Language });
    if (!(await this.save.run({ language: language as Language }))) {
      await this.i18n.setLanguage(previous, { persist: false });
      this.session.patchUser({ language: previous as Language });
    }
  }

  protected async chooseTheme(mode: ThemePreference): Promise<void> {
    const previous = this.theme.preference();
    if (mode === previous) {
      return;
    }
    this.theme.setPreference(mode, { persist: false });
    this.session.patchUser({ theme: themeToApi(mode) });
    if (!(await this.save.run({ theme: themeToApi(mode) }))) {
      this.theme.setPreference(previous, { persist: false });
      this.session.patchUser({ theme: themeToApi(previous) });
    }
  }
}
