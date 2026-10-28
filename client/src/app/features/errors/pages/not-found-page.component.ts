import { ChangeDetectionStrategy, Component } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorPageLayoutComponent } from './error-page-layout.component';

@Component({
  selector: 'fs-not-found-page',
  imports: [ErrorPageLayoutComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-error-page-layout
      code="404"
      icon="pi pi-map"
      [title]="'errorPages.notFound.title' | t"
      [text]="'errorPages.notFound.text' | t"
      testId="errorPages.notFound"
      data-testid="errorPages.notFound"
    />
  `,
})
export class NotFoundPageComponent {}
