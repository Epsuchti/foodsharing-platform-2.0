import { Injectable, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

import { I18nService } from './i18n.service';

/**
 * Route `title` values are i18n keys (`title: 'dashboard.page.title'`); the document title becomes
 * "<translated> · foodsharing" and follows language switches.
 */
@Injectable({ providedIn: 'root' })
export class I18nTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly i18n = inject(I18nService);
  private lastKey: string | undefined;

  constructor() {
    super();
    effect(() => {
      this.i18n.language();
      this.i18n.ready();
      this.apply(this.lastKey);
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.lastKey = this.buildTitle(snapshot);
    this.apply(this.lastKey);
  }

  private apply(key: string | undefined): void {
    const brand = this.i18n.t('common.appName');
    this.title.setTitle(key ? `${this.i18n.t(key)} · ${brand}` : brand);
  }
}
