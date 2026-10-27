import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ShellComponent } from './layout/shell.component';

@Component({
  selector: 'fs-root',
  imports: [ShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<fs-shell />`,
})
export class App {}
