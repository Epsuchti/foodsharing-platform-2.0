import { FsDateInputComponent } from './fs-date-input.component';
import { FsDatetimeInputComponent } from './fs-datetime-input.component';
import { FsTimeInputComponent } from './fs-time-input.component';

export { FsDateInputComponent, FsDatetimeInputComponent, FsTimeInputComponent };
export * from './date-input.util';

/** The shared date, datetime and time inputs (decision 150); native date/time inputs are not used. */
export const DATE_INPUT_IMPORTS = [FsDateInputComponent, FsDatetimeInputComponent, FsTimeInputComponent] as const;
