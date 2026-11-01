/**
 * Template shared by the three date inputs; the mode comes from `timeOnly`/`showTime` of the component class.
 * `showOnFocus` is off so typing is not covered by the popup: the calendar opens from the icon button.
 */
export const DATE_INPUT_TEMPLATE = `
  <p-datepicker
    [inputId]="inputId()"
    [ngModel]="pickerValue()"
    [ngModelOptions]="{ standalone: true }"
    [disabled]="disabled()"
    [required]="required()"
    [invalid]="invalid()"
    [minDate]="minDate()"
    [maxDate]="maxDate()"
    [timeOnly]="timeOnly"
    [showTime]="showTime"
    hourFormat="24"
    dateFormat="dd.mm.yy"
    [firstDayOfWeek]="1"
    [keepInvalid]="true"
    [showOnFocus]="false"
    [showIcon]="true"
    iconDisplay="input"
    [showButtonBar]="!required()"
    [showClear]="!required()"
    appendTo="body"
    [fluid]="true"
    [placeholder]="placeholderText()"
    [ariaLabel]="ariaLabel()"
    (onFocus)="onFocus()"
    (onBlur)="onBlur()"
    (keydown.enter)="commitText()"
    (onSelect)="onSelect($event)"
    (onClear)="onClear()"
    (onClearClick)="onClear()"
  >
    <ng-template #inputicon let-clickCallBack="clickCallBack">
      <button
        type="button"
        class="fs-date-input-trigger"
        [attr.aria-label]="openCalendarText()"
        [disabled]="disabled()"
        (click)="clickCallBack($event)"
      >
        <i [class]="timeOnly ? 'pi pi-clock' : 'pi pi-calendar'" aria-hidden="true"></i>
      </button>
    </ng-template>
  </p-datepicker>
  @if (parseError()) {
    <small class="fs-date-input-error" role="alert" [id]="errorId">{{ invalidText() }}</small>
  }
`;

export const DATE_INPUT_STYLES = `
  :host { display: block; min-width: 0; }
  .fs-date-input-trigger {
    display: inline-flex; align-items: center; justify-content: center;
    padding: 0; border: 0; background: none; color: inherit; cursor: pointer; font: inherit;
  }
  .fs-date-input-trigger:disabled { cursor: default; opacity: 0.6; }
  .fs-date-input-trigger:focus-visible { outline: 2px solid var(--p-focus-ring-color, currentColor); outline-offset: 2px; border-radius: 2px; }
  .fs-date-input-error { display: block; margin-top: 0.25rem; color: var(--fs-danger); }
`;
