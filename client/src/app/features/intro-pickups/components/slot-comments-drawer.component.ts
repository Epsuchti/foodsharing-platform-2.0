import { ChangeDetectionStrategy, Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Drawer } from '@openng/optimus-ui/drawer';
import { Textarea } from '@openng/optimus-ui/textarea';

import { IntroPickupAdminService, IntroPickupGuideService, SlotComment } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/**
 * Comments on one booking (EA13): guides note how the newcomer did; user managers add admin comments (marked).
 * The verification queue shows them later.
 */
@Component({
  selector: 'fs-slot-comments-drawer',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, Drawer, FormsModule, Textarea, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-drawer
      [visible]="!!slotId()"
      (visibleChange)="!$event && slotId.set(null)"
      position="right"
      [header]="'introPickups.comments.title' | t: { name: userName() }"
      [style]="{ width: 'min(28rem, 100vw)' }"
    >
      <div class="stack" data-testid="introPickups.comments">
        <fs-data-state [state]="comments" skeleton="list" [skeletonRows]="2" emptyIcon="pi pi-comments" [emptyTitle]="'introPickups.comments.empty' | t" [compact]="true" data-testid="introPickups.comments.state">
          <ng-template [fsData]="comments" let-list>
            <ul class="list">
              @for (comment of list; track comment.id) {
                <li class="comment" [class.comment--admin]="comment.adminComment" data-testid="introPickups.comments.item">
                  <div class="comment__head">
                    <fs-user-chip [name]="comment.author.displayName" [avatarUrl]="comment.author.avatarUrl" [subtitle]="comment.createdAt | fsDate: 'dateTime'" />
                    @if (comment.adminComment) {
                      <span class="tag tag--warn" data-testid="introPickups.comments.admin">{{ 'introPickups.comments.adminTag' | t }}</span>
                    }
                  </div>
                  <p>{{ comment.text }}</p>
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
        <div class="field">
          <label for="slot-comment">{{ (admin() ? 'introPickups.comments.addAdmin' : 'introPickups.comments.add') | t }}</label>
          <textarea pTextarea id="slot-comment" rows="3" [ngModel]="text()" (ngModelChange)="text.set($event)" data-testid="introPickups.comments.text"></textarea>
        </div>
        <div class="actions">
          <p-button [label]="'introPickups.comments.send' | t" icon="pi pi-send" [disabled]="!text().trim()" [loading]="add.busy()" (onClick)="add.run()" data-testid="introPickups.comments.send" />
        </div>
      </div>
    </p-drawer>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: contents;
    }
    .comment {
      display: grid;
      gap: 0.35rem;
      padding: 0.75rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
    }
    .comment--admin {
      border-color: var(--fs-warning-border);
      background: var(--fs-warning-soft);
    }
    .comment__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .comment p {
      margin: 0;
      white-space: pre-line;
      overflow-wrap: anywhere;
    }
  `,
})
export class SlotCommentsDrawerComponent {
  private readonly guideApi = inject(IntroPickupGuideService);
  private readonly adminApi = inject(IntroPickupAdminService);

  /** Open for this slot; null closes. */
  readonly slotId = model<string | null>(null);
  readonly userName = input('');
  /** User managers write admin comments. */
  readonly admin = input(false);
  readonly changed = output<void>();
  protected readonly text = signal('');

  protected readonly comments = loadResource<SlotComment[], string | null>({
    params: () => this.slotId() ?? undefined,
    loader: (slotId) => this.guideApi.listSlotComments({ slotId: slotId! }),
  });

  protected readonly add = runMutation(
    () => {
      const slotId = this.slotId()!;
      const slotCommentCreate = { comment: this.text().trim() };
      return this.admin()
        ? this.adminApi.addAdminComment({ slotId, slotCommentCreate })
        : this.guideApi.addSlotComment({ slotId, slotCommentCreate });
    },
    {
      successToastKey: 'introPickups.comments.added',
      onSuccess: () => {
        this.text.set('');
        this.comments.reload();
        this.changed.emit();
      },
    },
  );
}
