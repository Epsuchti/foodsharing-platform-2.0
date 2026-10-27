import { Directive, TemplateRef, inject, input } from '@angular/core';

import { ApiErrorView } from '../../core/data/api-error';
import { DataState } from '../../core/data/data-state';

export interface DataTemplateContext<T> {
  $implicit: T;
  data: T;
}

export interface ErrorTemplateContext {
  $implicit: ApiErrorView | undefined;
  error: ApiErrorView | undefined;
  retry: () => void;
}

/**
 * Content shown on success (and while refreshing / offline with cached data). Preferred over plain content
 * projection because the template is only instantiated when data exists:
 * `<ng-template [fsData]="sessions" let-list>…</ng-template>` (binding the state types `let-list`;
 * a plain `fsData` attribute works too, with an untyped context).
 */
@Directive({ selector: 'ng-template[fsData]' })
export class DataTemplateDirective<T = unknown> {
  /** Only used for template type inference. */
  readonly fsData = input<DataState<T> | ''>('');
  readonly template = inject<TemplateRef<DataTemplateContext<T>>>(TemplateRef);

  static ngTemplateContextGuard<T>(
    _dir: DataTemplateDirective<T>,
    _ctx: unknown,
  ): _ctx is DataTemplateContext<T> {
    return true;
  }
}

/** Replaces the default empty state: `<ng-template fsEmpty>…</ng-template>` */
@Directive({ selector: 'ng-template[fsEmpty]' })
export class EmptyTemplateDirective {
  readonly template = inject<TemplateRef<unknown>>(TemplateRef);
}

/** Replaces the default error state: `<ng-template fsError let-error let-retry="retry">…</ng-template>` */
@Directive({ selector: 'ng-template[fsError]' })
export class ErrorTemplateDirective {
  readonly template = inject<TemplateRef<ErrorTemplateContext>>(TemplateRef);

  static ngTemplateContextGuard(
    _dir: ErrorTemplateDirective,
    _ctx: unknown,
  ): _ctx is ErrorTemplateContext {
    return true;
  }
}

/** Replaces the skeleton preset: `<ng-template fsSkeleton>…</ng-template>` */
@Directive({ selector: 'ng-template[fsSkeleton]' })
export class SkeletonTemplateDirective {
  readonly template = inject<TemplateRef<unknown>>(TemplateRef);
}
