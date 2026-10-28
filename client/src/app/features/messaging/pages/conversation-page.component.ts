import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { TPipe } from '../../../core/i18n/t.pipe';
import { ConversationViewComponent } from '../components/conversation-view.component';

/** /messages/:conversationId (N1): the open conversation beside the list (alone on narrow screens). */
@Component({
  selector: 'fs-conversation-page',
  imports: [ConversationViewComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (conversationId(); as id) {
      <fs-conversation-view [conversationId]="id" (left)="leave()">
        <a fsConversationBack class="back" routerLink="/messages" [attr.aria-label]="'messaging.backToList' | t" data-testid="messaging.conversation.back">
          <i class="pi pi-arrow-left" aria-hidden="true"></i>
        </a>
      </fs-conversation-view>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-height: 0;
    }
    fs-conversation-view {
      flex: 1;
    }
    .back {
      display: none;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      border-radius: 50%;
      color: var(--fs-text);
    }
    @media (max-width: 47.99rem) {
      .back {
        display: grid;
      }
    }
  `,
})
export class ConversationPageComponent {
  private readonly router = inject(Router);
  protected readonly conversationId = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((params) => params.get('conversationId'))),
  );

  protected leave(): void {
    void this.router.navigate(['/messages']);
  }
}
