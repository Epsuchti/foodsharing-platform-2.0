import {
  DestroyRef,
  Injector,
  Signal,
  assertInInjectionContext,
  inject,
  signal,
} from '@angular/core';
import { AbstractControl, FormGroup } from '@angular/forms';
import { MessageService } from '@openng/optimus-ui/api';
import { Observable, Subscription } from 'rxjs';

import { I18nService } from '../i18n/i18n.service';
import { ApiErrorView, errorParams, toApiErrorView } from './api-error';

export interface MutationOptions<T> {
  /** Server field errors (ApiError.fieldErrors) are set on the matching controls as `{ server: code }`. */
  form?: FormGroup;
  /** i18n key of a success toast. */
  successToastKey?: string;
  onSuccess?: (value: T) => void;
  onError?: (error: ApiErrorView) => void;
  /** Applied before the call, rolled back on error (e.g. slot sign-up, reactions). */
  optimistic?: { apply(): void; rollback(): void };
  /** Default true: errors that are not field errors are shown as an error toast. */
  errorToast?: boolean;
  injector?: Injector;
}

export interface MutationState<A extends unknown[] = []> {
  readonly busy: Signal<boolean>;
  readonly error: Signal<ApiErrorView | undefined>;
  /** Starts the call; ignored while busy (no double submit). Resolves true on success, never rejects. */
  run(...args: A): Promise<boolean>;
}

/** Sets server-side field errors on the form; returns true when at least one control matched. */
export function applyFieldErrors(form: FormGroup, error: ApiErrorView): boolean {
  let matched = false;
  for (const fieldError of error.fieldErrors) {
    const control: AbstractControl | null = form.get(fieldError.field);
    if (control) {
      control.setErrors({
        ...(control.errors ?? {}),
        server: fieldError.code,
        serverMessage: fieldError.message,
      });
      control.markAsTouched();
      matched = true;
    }
  }
  return matched;
}

/**
 * Runs a mutating API call with busy state, double-submit protection, field errors on the form, error toast
 * and optional optimistic update with rollback. The form keeps its values on error.
 *
 * ```ts
 * readonly save = runMutation(() => this.usersApi.updateMySettings({ userSettingsUpdate: this.form.value }),
 *   { form: this.form, successToastKey: 'account.settings.saved' });
 * // template: <p-button [loading]="save.busy()" (onClick)="save.run()" />
 * ```
 */
export function runMutation<T, A extends unknown[] = []>(
  call: (...args: A) => Observable<T>,
  options: MutationOptions<T> = {},
): MutationState<A> {
  if (!options.injector) {
    assertInInjectionContext(runMutation);
  }
  const injector = options.injector ?? inject(Injector);
  const messages = injector.get(MessageService);
  const i18n = injector.get(I18nService);
  const busy = signal(false);
  const error = signal<ApiErrorView | undefined>(undefined);
  let subscription: Subscription | null = null;
  injector.get(DestroyRef).onDestroy(() => subscription?.unsubscribe());

  const run = (...args: A): Promise<boolean> => {
    if (busy()) {
      return Promise.resolve(false);
    }
    busy.set(true);
    error.set(undefined);
    options.optimistic?.apply();
    return new Promise<boolean>((resolve) => {
      let value: T | undefined;
      try {
        subscription = call(...args).subscribe({
          next: (next) => {
            value = next;
          },
          error: (thrown: unknown) => {
            busy.set(false);
            const view = toApiErrorView(thrown);
            options.optimistic?.rollback();
            error.set(view);
            const fieldErrorsShown = options.form ? applyFieldErrors(options.form, view) : false;
            // 401 is handled globally by the session-expired dialog
            if ((options.errorToast ?? true) && !fieldErrorsShown && view.status !== 401) {
              messages.add({
                severity: 'error',
                summary: i18n.t('common.error.title'),
                detail: i18n.error(view.code, errorParams(view)),
                life: 8000,
              });
            }
            options.onError?.(view);
            resolve(false);
          },
          complete: () => {
            busy.set(false);
            if (options.successToastKey) {
              messages.add({
                severity: 'success',
                summary: i18n.t(options.successToastKey),
                life: 4000,
              });
            }
            options.onSuccess?.(value as T);
            resolve(true);
          },
        });
      } catch (thrown) {
        busy.set(false);
        options.optimistic?.rollback();
        error.set(toApiErrorView(thrown));
        resolve(false);
      }
    });
  };

  return { busy: busy.asReadonly(), error: error.asReadonly(), run };
}
