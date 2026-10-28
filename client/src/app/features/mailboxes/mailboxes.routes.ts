import { Routes } from '@angular/router';

import { Role } from '../../api';
import { authGuard } from '../../core/auth/auth.guard';

const page = () => import('./pages/mailbox-page.component').then((m) => m.MailboxPageComponent);

/**
 * Mounted at `mailboxes` in app.routes.ts (M5 MB1–MB6): `/mailboxes`, `/mailboxes/new`,
 * `/mailboxes/:mailboxId/compose`, `/mailboxes/:mailboxId/:folder[/:mailId]`. Mailboxes need role ≥ STORE_MANAGER
 * (A35 `mayHaveMailbox`); the server decides per mailbox.
 */
export const MAILBOXES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    data: { roles: [Role.StoreManager] },
    children: [
      { path: '', title: 'mailboxes.title', loadComponent: page },
      {
        path: 'new',
        title: 'mailboxes.create.title',
        loadComponent: () =>
          import('./pages/mailbox-create-page.component').then((m) => m.MailboxCreatePageComponent),
      },
      {
        path: ':mailboxId/compose',
        title: 'mailboxes.compose.title',
        loadComponent: () => import('./pages/compose-page.component').then((m) => m.ComposePageComponent),
      },
      { path: ':mailboxId/:folder', title: 'mailboxes.title', loadComponent: page },
      { path: ':mailboxId/:folder/:mailId', title: 'mailboxes.title', loadComponent: page },
    ],
  },
];
