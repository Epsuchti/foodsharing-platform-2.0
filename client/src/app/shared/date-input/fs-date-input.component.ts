import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePicker } from '@openng/optimus-ui/datepicker';

import { DateInputBase } from './date-input-base';
import { DATE_INPUT_STYLES, DATE_INPUT_TEMPLATE } from './date-input-template';
import { dateToIsoDate, isoDateToDate, parseDisplayDate } from './date-input.util';

/** Date field `dd.mm.yyyy` with a calendar; value `yyyy-MM-dd` or `''` (decision 150). */
@Component({
  selector: 'fs-date-input',
  imports: [DatePicker, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FsDateInputComponent), multi: true }],
  host: { '[attr.data-testid]': 'testId()' },
  template: DATE_INPUT_TEMPLATE,
  styles: DATE_INPUT_STYLES,
})
export class FsDateInputComponent extends DateInputBase {
  protected readonly timeOnly = false;
  protected readonly showTime = false;

  protected parse(text: string): string | null {
    return parseDisplayDate(text);
  }

  protected toDate(value: string | null | undefined): Date | null {
    return isoDateToDate(value);
  }

  protected fromDate(date: Date): string {
    return dateToIsoDate(date);
  }

  protected defaultPlaceholder(): string {
    return this.i18n.t('common.dateInput.placeholder');
  }
}
