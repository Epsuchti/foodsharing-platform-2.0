import { Pipe, PipeTransform, inject } from '@angular/core';

import { I18nService } from './i18n.service';
import { MessageParams } from './message-format';

/**
 * `{{ 'common.save' | t }}`, `{{ 'x.items' | t: { count: n } }}`. Impure so the text follows language
 * switches; the lookup is a map read, so this is cheap.
 */
@Pipe({ name: 't', pure: false })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: string | null | undefined, params?: MessageParams): string {
    return key ? this.i18n.t(key, params) : '';
  }
}
