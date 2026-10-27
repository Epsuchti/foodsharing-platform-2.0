import { DataStateComponent } from './data-state.component';
import {
  DataTemplateDirective,
  EmptyTemplateDirective,
  ErrorTemplateDirective,
  SkeletonTemplateDirective,
} from './data-state.directives';
import { EmptyStateComponent } from './empty-state.component';
import { ErrorStateComponent } from './error-state.component';
import { SkeletonPresetComponent } from './skeleton-presets.component';

export * from './data-state.component';
export * from './data-state.directives';
export * from './empty-state.component';
export * from './error-state.component';
export * from './skeleton-presets.component';

/** Everything a page needs for DataState rendering: `imports: [...DATA_STATE_IMPORTS]`. */
export const DATA_STATE_IMPORTS = [
  DataStateComponent,
  DataTemplateDirective,
  EmptyTemplateDirective,
  ErrorTemplateDirective,
  SkeletonTemplateDirective,
  EmptyStateComponent,
  ErrorStateComponent,
  SkeletonPresetComponent,
] as const;
