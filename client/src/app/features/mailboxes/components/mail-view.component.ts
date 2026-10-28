import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { Mail, MailFolder, MailboxesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { formatSize, sanitizeMailHtml } from '../mailboxes.resource';

/**
 * One mail (MB3): headers, HTML in a sandboxed iframe (server jsoup + DOMPurify, remote images removed) with a
 * plain-text toggle, attachments, bounce banner, reply / reply all / forward / unread / trash / restore / delete.
 * Opening marks it read (`changed` lets the list refetch).
 */
@Component({
  selector: 'fs-mail-view',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="mail" skeleton="card" data-testid="mailboxes.mail.state">
      <ng-template [fsData]="mail" let-m>
        <header class="head">
          <ng-content select="[fsMailBack]" />
          <h2 class="subject" data-testid="mailboxes.mail.subject">{{ m.subject || ('mailboxes.noSubject' | t) }}</h2>
        </header>
        @if (m.bounce) {
          <p class="bounce" role="alert" data-testid="mailboxes.mail.bounce">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ 'mailboxes.mail.bounceText' | t }}
          </p>
        }
        <dl class="meta">
          <dt>{{ 'mailboxes.mail.from' | t }}</dt>
          <dd data-testid="mailboxes.mail.from"><span class="addr">{{ label(m.from) }}</span></dd>
          <dt>{{ 'mailboxes.mail.to' | t }}</dt>
          <dd data-testid="mailboxes.mail.to">
            @for (a of m.to; track a.address) {
              <span class="addr">{{ label(a) }}</span>
            }
          </dd>
          @if (m.cc.length) {
            <dt>{{ 'mailboxes.mail.cc' | t }}</dt>
            <dd>
              @for (a of m.cc; track a.address) {
                <span class="addr">{{ label(a) }}</span>
              }
            </dd>
          }
          @if (m.receivedAt) {
            <dt>{{ 'mailboxes.mail.date' | t }}</dt>
            <dd><time [attr.datetime]="m.receivedAt">{{ m.receivedAt | fsDate: 'dateTime' }}</time></dd>
          }
        </dl>
        <div class="actions">
          <p-button [label]="'mailboxes.mail.reply' | t" icon="pi pi-reply" size="small" [outlined]="true" [routerLink]="['/mailboxes', m.mailboxId, 'compose']" [queryParams]="{ replyTo: m.id }" data-testid="mailboxes.mail.reply" />
          <p-button [label]="'mailboxes.mail.replyAll' | t" icon="pi pi-users" size="small" [outlined]="true" [routerLink]="['/mailboxes', m.mailboxId, 'compose']" [queryParams]="{ replyTo: m.id, all: 1 }" data-testid="mailboxes.mail.replyAll" />
          <p-button [label]="'mailboxes.mail.forward' | t" icon="pi pi-share-alt" size="small" [outlined]="true" [routerLink]="['/mailboxes', m.mailboxId, 'compose']" [queryParams]="{ forwardOf: m.id }" data-testid="mailboxes.mail.forward" />
          <p-button [label]="'mailboxes.mail.markUnread' | t" icon="pi pi-eye-slash" size="small" [text]="true" (onClick)="update.run(m, { read: false })" data-testid="mailboxes.mail.markUnread" />
          @if (m.folder === folders.Trash) {
            <p-button [label]="'mailboxes.mail.restore' | t" icon="pi pi-undo" size="small" [text]="true" (onClick)="update.run(m, { folder: folders.Inbox })" data-testid="mailboxes.mail.restore" />
            <p-button [label]="'mailboxes.mail.delete' | t" icon="pi pi-times" size="small" [text]="true" severity="danger" (onClick)="remove.run(m)" data-testid="mailboxes.mail.delete" />
          } @else {
            <p-button [label]="'mailboxes.mail.trash' | t" icon="pi pi-trash" size="small" [text]="true" severity="danger" (onClick)="update.run(m, { folder: folders.Trash })" data-testid="mailboxes.mail.trash" />
          }
        </div>
        @if (m.attachments.length) {
          <ul class="attachments" [attr.aria-label]="'mailboxes.attachments' | t: { count: m.attachments.length }">
            @for (a of m.attachments; track a.id) {
              <li>
                <a [href]="a.url" [attr.download]="a.fileName" data-testid="mailboxes.mail.attachment" [attr.data-name]="a.fileName">
                  <i class="pi pi-paperclip" aria-hidden="true"></i>{{ a.fileName }}
                </a>
                <span class="muted">{{ size(a.size) }}</span>
              </li>
            }
          </ul>
        }
        @if (m.bodyHtml && !plain()) {
          <iframe
            class="body"
            sandbox="allow-popups allow-popups-to-escape-sandbox"
            referrerpolicy="no-referrer"
            [title]="'mailboxes.mail.body' | t"
            [srcdoc]="html()"
            data-testid="mailboxes.mail.html"
          ></iframe>
        } @else {
          <pre class="text" data-testid="mailboxes.mail.text">{{ m.bodyText }}</pre>
        }
        @if (m.bodyHtml) {
          <p-button
            [label]="(plain() ? 'mailboxes.mail.showHtml' : 'mailboxes.mail.showText') | t"
            size="small"
            [text]="true"
            (onClick)="plain.set(!plain())"
            data-testid="mailboxes.mail.toggleText"
          />
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.75rem;
    }
    .head {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .subject {
      margin: 0;
      font-size: 1.2rem;
      overflow-wrap: anywhere;
    }
    .bounce {
      display: flex;
      gap: 0.5rem;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-warning-bg, #fff4e5);
      color: var(--fs-warning-text, #8a4b00);
    }
    .meta {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.2rem 0.75rem;
      margin: 0.5rem 0;
      font-size: 0.9rem;
    }
    .meta dt {
      color: var(--fs-text-muted);
    }
    .meta dd {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
      margin: 0;
    }
    .addr {
      padding: 0.05rem 0.5rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      overflow-wrap: anywhere;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .attachments {
      display: grid;
      gap: 0.3rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .attachments a {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      min-height: 2rem;
      margin-right: 0.5rem;
      color: var(--fs-primary-strong);
    }
    .muted {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .body {
      width: 100%;
      min-height: 24rem;
      height: 60vh;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-sm);
      background: #fff;
    }
    .text {
      margin: 0;
      font: inherit;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
  `,
})
export class MailViewComponent {
  private readonly api = inject(MailboxesService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly mailId = input.required<string>();
  /** Read state, folder or flags changed (the list refetches). */
  readonly changed = output<void>();
  /** The mail left the current folder (trash, restore, delete). */
  readonly removed = output<void>();

  protected readonly folders = MailFolder;
  protected readonly plain = signal(false);

  protected readonly mail = loadResource<Mail, string>({
    params: () => this.mailId(),
    loader: (mailId) => this.api.getMail({ mailId }),
  });

  protected readonly html = computed<SafeHtml>(() =>
    // sanitised twice (server + DOMPurify) and rendered in a sandbox without scripts or same-origin access
    this.sanitizer.bypassSecurityTrustHtml(sanitizeMailHtml(this.mail.data()?.bodyHtml ?? '')),
  );

  constructor() {
    // opening marks the mail read on the server: the list and the unread counts refetch
    let notified: string | null = null;
    effect(() => {
      const mail = this.mail.data();
      if (mail && mail.id !== notified) {
        notified = mail.id;
        untracked(() => this.changed.emit());
      }
    });
  }

  protected label(address: { address: string; name?: string }): string {
    return address.name ? `${address.name} <${address.address}>` : address.address;
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  protected readonly update = runMutation(
    (mail: Mail, mailUpdate: { read?: boolean; folder?: MailFolder }) =>
      this.api.moveOrFlagMail({ mailId: mail.id, mailUpdate }),
    {
      onSuccess: () => {
        this.removed.emit();
      },
    },
  );

  protected readonly remove = runMutation((mail: Mail) => this.api.deleteMail({ mailId: mail.id }), {
    successToastKey: 'mailboxes.mail.deleted',
    onSuccess: () => this.removed.emit(),
  });
}
