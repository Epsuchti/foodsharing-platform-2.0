import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePicker } from '@openng/optimus-ui/datepicker';

import { DateInputBase } from './date-input-base';
import { DATE_INPUT_STYLES, DATE_INPUT_TEMPLATE } from './date-input-template';
import { dateToTime, parseDisplayTime, timeToDate } from './date-input.util';

/** 24h time `HH:mm` with a time popup; value `HH:mm` or `''` (decision 150). */
@Component({
  selector: 'fs-time-input',
  imports: [DatePicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FsTimeInputComponent), multi: true }],
  host: { '[attr.data-testid]': 'testId()' },
  template: DATE_INPUT_TEMPLATE,
  styles: DATE_INPUT_STYLES,
})
export class FsTimeInputComponent extends DateInputBase {
  protected readonly timeOnly = true;
  protected readonly showTime = false;

  protected parse(text: string): string | null {
    return parseDisplayTime(text);
  }

  protected toDate(value: string | null | undefined): Date | null {
    return timeToDate(value);
  }

  protected fromDate(date: Date): string {
    return dateToTime(date);
  }

  protected defaultPlaceholder(): string {
    return this.i18n.t('common.dateInput.timePlaceholder');
  }
}
