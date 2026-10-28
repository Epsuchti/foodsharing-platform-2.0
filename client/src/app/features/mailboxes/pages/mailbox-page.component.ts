import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { map } from 'rxjs';

import { MailFolder, MailPage, Mailbox, MailboxesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { MailViewComponent } from '../components/mail-view.component';
import { MailboxManageDialogComponent } from '../components/mailbox-manage-dialog.component';
import { MailboxStore } from '../mailboxes.resource';

const FOLDERS: { id: MailFolder; icon: string }[] = [
  { id: MailFolder.Inbox, icon: 'pi pi-inbox' },
  { id: MailFolder.Sent, icon: 'pi pi-send' },
  { id: MailFolder.Trash, icon: 'pi pi-trash' },
];

/**
 * /mailboxes[/:mailboxId/:folder[/:mailId]] (MB2, MB3): mailboxes with unread counts and folders on the left, the
 * folder's mails (search, paging, unread bold, answered/bounce/attachment icons) and the open mail. Narrow screens
 * stack the panes and show only the open mail when one is selected.
 */
@Component({
  selector: 'fs-mailbox-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    InputText,
    MailViewComponent,
    MailboxManageDialogComponent,
    PageHeaderComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.has-mail]': '!!mailId()' },
  template: `
    <fs-page-header class="title" [title]="'mailboxes.title' | t" icon="pi pi-envelope">
      <p-button [label]="'mailboxes.create.button' | t" icon="pi pi-plus" [outlined]="true" routerLink="/mailboxes/new" data-testid="mailboxes.create" />
    </fs-page-header>
    <fs-data-state
      [state]="mailboxes"
      skeleton="list"
      [emptyTitle]="'mailboxes.emptyTitle' | t"
      [emptyText]="'mailboxes.emptyText' | t"
      emptyIcon="pi pi-envelope"
      data-testid="mailboxes.rail.state"
    >
      <ng-template [fsData]="mailboxes" let-boxes>
        <div class="panes">
          <nav class="rail fs-card" [attr.aria-label]="'mailboxes.title' | t">
            @for (box of boxes; track box.id) {
              <div class="box" data-testid="mailboxes.rail.mailbox" [attr.data-address]="box.address">
                <div class="box__head">
                  <span class="box__name">{{ box.name }}</span>
                  @if (box.mayManage) {
                    <p-button icon="pi pi-cog" size="small" [text]="true" [rounded]="true" severity="secondary" [ariaLabel]="('mailboxes.manage.title' | t) + ': ' + box.name" (onClick)="managed.set(box)" data-testid="mailboxes.rail.manage" />
                  }
                </div>
                <span class="box__address">{{ box.address }}</span>
                <ul class="folders">
                  @for (f of folders; track f.id) {
                    <li>
                      <a
                        class="folder"
                        [class.folder--active]="box.id === mailboxId() && f.id === folder()"
                        [routerLink]="['/mailboxes', box.id, f.id.toLowerCase()]"
                        [attr.aria-current]="box.id === mailboxId() && f.id === folder() ? 'page' : null"
                        [attr.data-testid]="'mailboxes.rail.folder.' + f.id"
                      >
                        <i [class]="f.icon" aria-hidden="true"></i>
                        {{ 'mailboxes.folder.' + f.id | t }}
                        @if (f.id === 'INBOX' && box.unreadCount > 0) {
                          <span class="badge" data-testid="mailboxes.rail.unread" [attr.aria-label]="'mailboxes.unread' | t: { count: box.unreadCount }">{{ box.unreadCount }}</span>
                        }
                      </a>
                    </li>
                  }
                </ul>
              </div>
            }
          </nav>

          <section class="list fs-card" [attr.aria-label]="'mailboxes.folder.' + folder() | t">
            <form class="search" (ngSubmit)="search.set(searchDraft.trim()); pageIndex.set(0)">
              <label class="sr-only" for="mail-search">{{ 'mailboxes.search' | t }}</label>
              <input pInputText id="mail-search" type="search" name="q" [placeholder]="'mailboxes.search' | t" [(ngModel)]="searchDraft" data-testid="mailboxes.list.search" />
              <p-button type="submit" icon="pi pi-search" [text]="true" [ariaLabel]="'mailboxes.search' | t" />
            </form>
            <fs-data-state [state]="mails" skeleton="list" [emptyTitle]="'mailboxes.list.empty' | t" emptyIcon="pi pi-inbox" data-testid="mailboxes.list.state">
              <ng-template [fsData]="mails" let-page>
                <ul class="mails">
                  @for (m of page.items; track m.id) {
                    <li>
                      <a
                        class="mail"
                        [class.mail--unread]="!m.read"
                        [class.mail--active]="m.id === mailId()"
                        [routerLink]="['/mailboxes', mailboxId(), folder().toLowerCase(), m.id]"
                        data-testid="mailboxes.list.mail"
                        [attr.data-subject]="m.subject"
                        [attr.data-read]="m.read"
                      >
                        <span class="mail__from">{{ folder() === 'SENT' ? (m.to[0]?.name || m.to[0]?.address) : (m.from.name || m.from.address) }}</span>
                        <span class="mail__icons">
                          @if (m.answered) {
                            <i class="pi pi-reply" role="img" [attr.aria-label]="'mailboxes.answered' | t" data-testid="mailboxes.list.answered"></i>
                          }
                          @if (m.bounce) {
                            <i class="pi pi-exclamation-triangle" role="img" [attr.aria-label]="'mailboxes.bounce' | t" data-testid="mailboxes.list.bounce"></i>
                          }
                          @if (m.hasAttachments) {
                            <i class="pi pi-paperclip" role="img" [attr.aria-label]="'mailboxes.attachments' | t: { count: m.attachmentCount }"></i>
                          }
                          @if (m.receivedAt) {
                            <time [attr.datetime]="m.receivedAt">{{ m.receivedAt | fsDate: 'date' }}</time>
                          }
                        </span>
                        <span class="mail__subject">{{ m.subject || ('mailboxes.noSubject' | t) }}</span>
                      </a>
                    </li>
                  }
                </ul>
                @if (page.page.totalPages > 1) {
                  <div class="pager">
                    <p-button icon="pi pi-angle-left" [text]="true" [disabled]="pageIndex() === 0" [ariaLabel]="'mailboxes.previous' | t" (onClick)="pageIndex.set(pageIndex() - 1)" />
                    <span>{{ pageIndex() + 1 }} / {{ page.page.totalPages }}</span>
                    <p-button icon="pi pi-angle-right" [text]="true" [disabled]="pageIndex() + 1 >= page.page.totalPages" [ariaLabel]="'mailboxes.next' | t" (onClick)="pageIndex.set(pageIndex() + 1)" data-testid="mailboxes.list.next" />
                  </div>
                }
              </ng-template>
            </fs-data-state>
          </section>

          <section class="view fs-card">
            @if (mailId(); as id) {
              <fs-mail-view [mailId]="id" (changed)="refresh()" (removed)="closeMail()">
                <a fsMailBack class="back" [routerLink]="['/mailboxes', mailboxId(), folder().toLowerCase()]" [attr.aria-label]="'mailboxes.backToList' | t">
                  <i class="pi pi-arrow-left" aria-hidden="true"></i>
                </a>
              </fs-mail-view>
            } @else {
              <div class="pick">
                <p class="muted">{{ 'mailboxes.pickMail' | t }}</p>
                @if (mailboxId(); as id) {
                  <p-button [label]="'mailboxes.compose.button' | t" icon="pi pi-pencil" [routerLink]="['/mailboxes', id, 'compose']" data-testid="mailboxes.compose" />
                }
              </div>
            }
          </section>
        </div>
      </ng-template>
    </fs-data-state>
    <fs-mailbox-manage-dialog [mailbox]="managed()" (closed)="managed.set(null)" (changed)="mailboxes.reload()" />
  `,
  styleUrl: '../mailboxes.scss',
})
export class MailboxPageComponent {
  private readonly api = inject(MailboxesService);
  private readonly router = inject(Router);
  private readonly store = inject(MailboxStore);
  private readonly params = toSignal(inject(ActivatedRoute).paramMap.pipe(map((p) => p)));

  protected readonly folders = FOLDERS;
  protected readonly mailboxId = computed(() => this.params()?.get('mailboxId') ?? null);
  protected readonly folder = computed<MailFolder>(() => {
    const value = (this.params()?.get('folder') ?? 'inbox').toUpperCase();
    return (Object.values(MailFolder) as string[]).includes(value) ? (value as MailFolder) : MailFolder.Inbox;
  });
  protected readonly mailId = computed(() => this.params()?.get('mailId') ?? null);
  protected readonly search = signal('');
  protected searchDraft = '';
  protected readonly pageIndex = signal(0);
  protected readonly managed = signal<Mailbox | null>(null);

  protected readonly mailboxes = loadResource<Mailbox[]>({
    loader: () => this.api.listMailboxes(),
    isEmpty: (list) => list.length === 0,
    keepDataOnError: true,
  });

  protected readonly mails = loadResource<
    MailPage,
    { mailboxId: string; folder: MailFolder; q: string; page: number }
  >({
    params: () => {
      const mailboxId = this.mailboxId();
      return mailboxId
        ? { mailboxId, folder: this.folder(), q: this.search(), page: this.pageIndex() }
        : undefined;
    },
    loader: ({ mailboxId, folder, q, page }) =>
      this.api.listMails({ mailboxId, folder, page, size: 25, ...(q ? { q } : {}) }),
    isEmpty: (page) => page.items.length === 0,
    keepDataOnError: true,
  });

  constructor() {
    // new mail (realtime), a reconnect or a local change: refetch in place (the open mail stays)
    let seen = this.store.version();
    effect(() => {
      const version = this.store.version();
      if (version !== seen) {
        seen = version;
        untracked(() => {
          this.mailboxes.reload();
          this.mails.reload();
        });
      }
    });
    // /mailboxes without an id opens the first mailbox's inbox
    effect(() => {
      const boxes = this.mailboxes.data();
      if (!this.mailboxId() && boxes?.length) {
        untracked(() => void this.router.navigate(['/mailboxes', boxes[0].id, 'inbox'], { replaceUrl: true }));
      }
    });
    // another folder or mailbox: back to page 1 without search
    effect(() => {
      this.mailboxId();
      this.folder();
      untracked(() => {
        this.pageIndex.set(0);
        this.search.set('');
        this.searchDraft = '';
      });
    });
  }

  protected refresh(): void {
    this.store.bump();
  }

  protected closeMail(): void {
    this.store.bump();
    void this.router.navigate(['/mailboxes', this.mailboxId(), this.folder().toLowerCase()]);
  }
}
