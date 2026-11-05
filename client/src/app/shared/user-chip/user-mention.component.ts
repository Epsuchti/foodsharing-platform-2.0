import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SessionService } from '../../core/auth/session.service';
import { TPipe } from '../../core/i18n/t.pipe';

/**
 * Inline "@Name" mention pill linking to `/profile/:userId` (mockup part 4). The look lives in the global `.fs-mention`
 * class (styles.scss) so forum mentions rendered by `fs-markdown` match; `fs-mention--me` adds a ring when the viewer
 * is the one mentioned.
 */
@Component({
  selector: 'fs-user-mention',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<a
    class="fs-mention"
    [class.fs-mention--me]="isMe()"
    [routerLink]="['/profile', userId()]"
    [attr.aria-label]="'shared.userMention.label' | t: { name: name() }"
    [attr.data-me]="isMe() ? '' : null"
    [attr.data-testid]="testId()"
    >&#64;{{ name() }}</a
  >`,
  styles: `
    :host {
      display: inline;
    }
  `,
})
export class UserMentionComponent {
  private readonly session = inject(SessionService);

  readonly name = input.required<string>();
  readonly userId = input.required<string>();
  readonly testId = input('shared.userMention');

  protected readonly isMe = computed(() => this.session.user()?.id === this.userId());
}
