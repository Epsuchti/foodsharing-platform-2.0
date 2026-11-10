import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';

import { MessagingService } from '../../api';
import { runMutation } from '../../core/data/mutation';
import { TPipe } from '../../core/i18n/t.pipe';

/**
 * "Nachricht" next to a waiting step's contact (review 2026-11-10 A4): finds or creates the direct conversation and
 * opens it. Its own component so the journey stepper (shell journey bar, initial bundle) loads it deferred.
 */
@Component({
  selector: 'fs-journey-message-button',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="fs-btn fs-btn--text fs-btn--sm"
      [disabled]="message.busy()"
      [attr.aria-label]="'shared.journey.messageAria' | t: { name: name() }"
      [attr.data-testid]="testId()"
      (click)="message.run(userId())"
    >
      <i class="pi pi-envelope" aria-hidden="true"></i>
      {{ 'shared.journey.message' | t }}
    </button>
  `,
})
export class JourneyMessageButtonComponent {
  private readonly messaging = inject(MessagingService);
  private readonly router = inject(Router);

  readonly userId = input.required<string>();
  readonly name = input.required<string>();
  readonly testId = input.required<string>();

  protected readonly message = runMutation(
    (userId: string) => this.messaging.lookupConversation({ conversationLookup: { memberIds: [userId] } }),
    { onSuccess: (conversation) => void this.router.navigate(['/messages', conversation.id]) },
  );
}
