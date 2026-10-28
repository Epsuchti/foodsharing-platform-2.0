import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { TPipe } from '../../core/i18n/t.pipe';

/** Right pane while no conversation is open (desktop). */
@Component({
  selector: 'fs-no-conversation',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="hint"><i class="pi pi-comments" aria-hidden="true"></i>{{ 'messaging.pickConversation' | t }}</p>`,
  styles: `
    .hint {
      display: grid;
      justify-items: center;
      gap: 0.75rem;
      margin: 4rem auto;
      color: var(--fs-text-muted);
    }
    .hint i {
      font-size: 2rem;
    }
  `,
})
export class NoConversationComponent {}

/** Mounted at `messages` in app.routes.ts (M5 N1): `/messages`, `/messages/new`, `/messages/:conversationId`. */
export const MESSAGING_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    title: 'messaging.title',
    loadComponent: () => import('./pages/messages-page.component').then((m) => m.MessagesPageComponent),
    children: [
      { path: '', component: NoConversationComponent },
      { path: 'new', component: NoConversationComponent, data: { compose: true } },
      {
        path: ':conversationId',
        loadComponent: () => import('./pages/conversation-page.component').then((m) => m.ConversationPageComponent),
      },
    ],
  },
];
