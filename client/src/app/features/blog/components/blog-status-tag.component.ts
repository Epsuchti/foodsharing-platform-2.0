import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Tag } from '@openng/optimus-ui/tag';

import { TPipe } from '../../../core/i18n/t.pipe';
import { blogStatus } from '../blog.util';

const SEVERITY = { published: 'success', awaitingCheck: 'warn', draft: 'secondary' } as const;
const ICON = {
  published: 'pi pi-globe',
  awaitingCheck: 'pi pi-hourglass',
  draft: 'pi pi-pencil',
} as const;

/** Published / awaiting check / draft (C2): the editors' view of a post. */
@Component({
  selector: 'fs-blog-status-tag',
  imports: [Tag, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-tag
      [value]="'blog.status.' + status() | t"
      [severity]="severity()"
      [icon]="icon()"
      [rounded]="true"
      [attr.data-status]="status()"
      [attr.data-testid]="testId()"
    />
  `,
})
export class BlogStatusTagComponent {
  readonly published = input.required<boolean>();
  readonly awaitingCheck = input<boolean | undefined>(false);
  readonly testId = input('blog.status');

  protected readonly status = computed(() =>
    blogStatus({ published: this.published(), awaitingCheck: this.awaitingCheck() }),
  );
  protected readonly severity = computed(() => SEVERITY[this.status()]);
  protected readonly icon = computed(() => ICON[this.status()]);
}
