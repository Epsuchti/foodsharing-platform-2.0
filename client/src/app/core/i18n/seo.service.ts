import { DOCUMENT, isPlatformServer } from '@angular/common';
import { Injectable, PLATFORM_ID, effect, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';

import { I18nService } from './i18n.service';
import { LANGUAGES, UiLanguage, htmlLang } from './languages';

/**
 * Placeholder origin in prerendered absolute URLs; SpaForwardingController replaces it with `app.base-url`
 * (the deploy origin is unknown at build time).
 */
export const PRERENDER_ORIGIN = 'https://origin.invalid';

/**
 * Per-language `<meta name="description">` / Open Graph locale, and the `hreflang` alternates of the landing
 * page (`/de`, `/gws`, `/fr`, `/it`, `/en`, x-default `/`) — plan §11.2 SEO, D11.5.3.
 */
@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly document = inject(DOCUMENT);
  private readonly meta = inject(Meta);
  private readonly i18n = inject(I18nService);
  private readonly server = isPlatformServer(inject(PLATFORM_ID));

  constructor() {
    effect(() => {
      const language = this.i18n.language();
      if (!this.i18n.ready()) {
        return;
      }
      this.meta.updateTag({ name: 'description', content: this.i18n.t('common.metaDescription') });
      this.meta.updateTag({ property: 'og:locale', content: htmlLang(language).replace('-', '_') });
      this.meta.updateTag({ property: 'og:title', content: this.i18n.t('common.appName') });
      this.meta.updateTag({
        property: 'og:description',
        content: this.i18n.t('common.metaDescription'),
      });
    });
  }

  /** Adds the alternates of the landing page; `current` is the language the page is shown in. */
  setLandingAlternates(current: UiLanguage | null): void {
    const origin = this.server ? PRERENDER_ORIGIN : this.document.location.origin;
    const links: { hreflang: string; href: string }[] = LANGUAGES.map((language) => ({
      hreflang: htmlLang(language) === 'de-CH' ? 'gsw' : htmlLang(language),
      href: `${origin}/${language}`,
    }));
    links.push({ hreflang: 'x-default', href: `${origin}/` });
    this.clearLandingAlternates();
    const head = this.document.head;
    for (const { hreflang, href } of links) {
      const link = this.document.createElement('link');
      link.setAttribute('rel', 'alternate');
      link.setAttribute('hreflang', hreflang);
      link.setAttribute('href', href);
      head.appendChild(link);
    }
    const canonical = this.document.createElement('link');
    canonical.setAttribute('rel', 'canonical');
    canonical.setAttribute('href', current ? `${origin}/${current}` : `${origin}/`);
    head.appendChild(canonical);
  }

  clearLandingAlternates(): void {
    this.document.head
      .querySelectorAll('link[rel="alternate"][hreflang], link[rel="canonical"]')
      .forEach((link) => link.remove());
  }
}
