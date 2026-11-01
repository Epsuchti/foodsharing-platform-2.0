import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePicker } from '@openng/optimus-ui/datepicker';

import { DateInputBase } from './date-input-base';
import { DATE_INPUT_STYLES, DATE_INPUT_TEMPLATE } from './date-input-template';
import { dateToIsoDateTime, isoDateTimeToDate, parseDisplayDateTime } from './date-input.util';

/**
 * Date and 24h time `dd.mm.yyyy HH:mm` with a calendar; value `yyyy-MM-ddTHH:mm` (wall time, as `datetime-local`
 * gave) or `''` (decision 150).
 */
@Component({
  selector: 'fs-datetime-input',
  imports: [DatePicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FsDatetimeInputComponent), multi: true }],
  host: { '[attr.data-testid]': 'testId()' },
  template: DATE_INPUT_TEMPLATE,
  styles: DATE_INPUT_STYLES,
})
export class FsDatetimeInputComponent extends DateInputBase {
  protected readonly timeOnly = false;
  protected readonly showTime = true;

  protected parse(text: string): string | null {
    return parseDisplayDateTime(text);
  }

  protected toDate(value: string | null | undefined): Date | null {
    return isoDateTimeToDate(value);
  }

  protected fromDate(date: Date): string {
    return dateToIsoDateTime(date);
  }

  protected defaultPlaceholder(): string {
    return `${this.i18n.t('common.dateInput.placeholder')} ${this.i18n.t('common.dateInput.timePlaceholder')}`;
  }
}
