import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { firstValueFrom, map } from 'rxjs';

import { Mail, MailboxesService, Upload } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimeFormatter } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { MailBodyEditorComponent } from '../components/mail-body-editor.component';
import { MailboxStore, formatSize, parseAddresses, textToHtml } from '../mailboxes.resource';

interface Original {
  mail: Mail | null;
}

/**
 * /mailboxes/:mailboxId/compose[?replyTo=&all=1|forwardOf=] (MB4): recipients (validated), subject, rich-text body
 * (`fs-mail-body-editor`, deferred until idle) with the quoted original for replies, attachments via /api/uploads (forwarding keeps the original's attachments on the
 * server). Sends from the mailbox address; 429/503 errors are shown by runMutation.
 */
@Component({
  selector: 'fs-compose-page',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, MailBodyEditorComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="titleKey() | t" icon="pi pi-pencil" [backLink]="['/mailboxes', mailboxId(), 'inbox']" [backLabel]="'mailboxes.title' | t" />
    <fs-data-state [state]="original" skeleton="card" data-testid="mailboxes.compose.state">
      <ng-template [fsData]="original" let-o>
        <form class="form fs-card" (ngSubmit)="send.run()">
          <label for="mail-to">{{ 'mailboxes.compose.to' | t }}</label>
          <input pInputText id="mail-to" name="to" [ngModel]="toValue()" (ngModelChange)="toValue.set($event)" [attr.aria-invalid]="invalid().length > 0" aria-describedby="mail-to-hint" data-testid="mailboxes.compose.to" />
          <small id="mail-to-hint" class="hint" [class.error]="invalid().length > 0" data-testid="mailboxes.compose.toHint">
            @if (invalid().length > 0) {
              {{ 'mailboxes.compose.invalid' | t: { addresses: invalid().join(', ') } }}
            } @else {
              {{ 'mailboxes.compose.toHint' | t }}
            }
          </small>
          <label for="mail-cc">{{ 'mailboxes.compose.cc' | t }}</label>
          <input pInputText id="mail-cc" name="cc" [(ngModel)]="cc" data-testid="mailboxes.compose.cc" />
          <label for="mail-subject">{{ 'mailboxes.compose.subject' | t }}</label>
          <input pInputText id="mail-subject" name="subject" maxlength="250" [(ngModel)]="subject" data-testid="mailboxes.compose.subject" />
          <span class="body-label" id="mail-body-label">{{ 'mailboxes.compose.body' | t }}</span>
          @defer (on idle) {
            <fs-mail-body-editor [value]="body()" (valueChange)="body.set($event)" labelledBy="mail-body-label" />
          } @placeholder (minimum 200ms) {
            <div class="body-placeholder" aria-hidden="true" data-testid="mailboxes.compose.bodyLoading"></div>
          }

          <div class="attachments">
            <label class="upload" for="mail-files">
              <i class="pi pi-paperclip" aria-hidden="true"></i>{{ 'mailboxes.compose.attach' | t }}
            </label>
            <input id="mail-files" class="sr-only" type="file" multiple (change)="pick($event)" data-testid="mailboxes.compose.files" />
            @if (upload.busy()) {
              <span class="hint" role="status"><i class="pi pi-spin pi-spinner" aria-hidden="true"></i> {{ 'mailboxes.compose.uploading' | t }}</span>
            }
            <ul>
              @for (file of uploads(); track file.id) {
                <li data-testid="mailboxes.compose.attachment" [attr.data-name]="file.fileName">
                  {{ file.fileName }} <span class="hint">{{ size(file.size) }}</span>
                  <p-button icon="pi pi-times" size="small" [text]="true" [rounded]="true" [ariaLabel]="('mailboxes.compose.removeAttachment' | t) + ': ' + file.fileName" (onClick)="detach(file)" />
                </li>
              }
              @if (o.mail && forward()) {
                @for (a of o.mail.attachments; track a.id) {
                  <li class="hint" data-testid="mailboxes.compose.forwardedAttachment">{{ a.fileName }} ({{ 'mailboxes.compose.forwarded' | t }})</li>
                }
              }
            </ul>
          </div>
          <div class="actions">
            <p-button type="submit" [label]="'mailboxes.compose.send' | t" icon="pi pi-send" [loading]="send.busy()" [disabled]="!canSend()" data-testid="mailboxes.compose.send" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .form {
      display: grid;
      gap: 0.4rem;
      padding: 1rem;
    }
    label, .body-label {
      margin-top: 0.4rem;
      font-weight: 600;
    }
    .hint {
      color: var(--fs-text-muted);
    }
    .error {
      color: var(--fs-danger-text, #b42318);
    }
    .attachments ul {
      margin: 0.25rem 0 0;
      padding-left: 1.25rem;
    }
    .upload {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      min-height: 2.5rem;
      padding: 0 0.75rem;
      border: 1px dashed var(--fs-border);
      border-radius: var(--fs-radius-sm);
      cursor: pointer;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
    .body-placeholder {
      height: 18.75rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-sm);
      background: var(--fs-surface-muted);
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      overflow: hidden;
      clip: rect(0 0 0 0);
      white-space: nowrap;
    }
  `,
})
export class ComposePageComponent {
  private readonly api = inject(MailboxesService);
  private readonly store = inject(MailboxStore);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly dates = inject(DateTimeFormatter);
  private readonly route = inject(ActivatedRoute);

  protected readonly mailboxId = toSignal(this.route.paramMap.pipe(map((p) => p.get('mailboxId')!)), {
    initialValue: this.route.snapshot.paramMap.get('mailboxId')!,
  });
  private readonly query = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  private readonly replyTo = computed(() => this.query().get('replyTo'));
  protected readonly forward = computed(() => this.query().get('forwardOf'));
  private readonly replyAll = computed(() => this.query().get('all') === '1');

  protected cc = '';
  protected subject = '';
  /** HTML from the rich-text editor (MB4). */
  protected readonly body = signal('');
  protected readonly uploads = signal<Upload[]>([]);
  protected readonly toValue = signal('');

  protected readonly titleKey = computed(() =>
    this.replyTo() ? 'mailboxes.compose.replyTitle' : this.forward() ? 'mailboxes.compose.forwardTitle' : 'mailboxes.compose.title',
  );

  protected readonly original = loadResource<Original, string>({
    params: () => this.replyTo() ?? this.forward() ?? '',
    loader: async (mailId) => ({
      mail: mailId ? await firstValueFrom(this.api.getMail({ mailId, keepUnread: true })) : null,
    }),
  });

  protected readonly invalid = computed(() => parseAddresses(this.toValue()).invalid);

  constructor() {
    effect(() => {
      const mail = this.original.data()?.mail;
      if (mail) {
        untracked(() => this.prefill(mail));
      }
    });
  }

  protected canSend(): boolean {
    return parseAddresses(this.toValue()).valid.length > 0 && this.invalid().length === 0 && !this.upload.busy();
  }

  protected size(bytes: number): string {
    return formatSize(bytes);
  }

  private prefill(mail: Mail): void {
    const when = mail.receivedAt ? this.dates.format(mail.receivedAt, 'dateTime') : '';
    const quoted = `<blockquote>${textToHtml(mail.bodyText ?? '')}</blockquote>`;
    if (this.replyTo()) {
      const sender = mail.replyTo ?? mail.from;
      const others = this.replyAll() ? [...mail.to, ...mail.cc].map((a) => a.address) : [];
      this.toValue.set(sender.address);
      this.cc = others.filter((a) => a !== sender.address).join(', ');
      this.subject = /^re:/i.test(mail.subject) ? mail.subject : `Re: ${mail.subject}`;
      this.body.set(`<p><br></p>${textToHtml(this.i18n.t('mailboxes.compose.wrote', { date: when, name: sender.name ?? sender.address }))}${quoted}`);
    } else {
      this.subject = /^fwd?:/i.test(mail.subject) ? mail.subject : `Fwd: ${mail.subject}`;
      this.body.set(`<p><br></p>${textToHtml(this.i18n.t('mailboxes.compose.forwardedHeader', { date: when, name: mail.from.name ?? mail.from.address }))}${textToHtml(mail.bodyText ?? '')}`);
    }
  }

  protected pick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = [...(input.files ?? [])];
    input.value = '';
    void (async () => {
      for (const file of files) {
        await this.upload.run(file);
      }
    })();
  }

  protected detach(file: Upload): void {
    this.uploads.update((list) => list.filter((u) => u.id !== file.id));
  }

  protected readonly upload = runMutation((file: File) => this.store.upload(file), {
    onSuccess: (uploaded: Upload) => this.uploads.update((list) => [...list, uploaded]),
  });

  protected readonly send = runMutation(
    () =>
      this.api.sendMail({
        mailboxId: this.mailboxId(),
        mailCompose: {
          to: parseAddresses(this.toValue()).valid,
          cc: parseAddresses(this.cc).valid,
          subject: this.subject.trim(),
          bodyHtml: this.body(),
          attachmentUploadIds: this.uploads().map((u) => u.id),
          ...(this.replyTo() ? { inReplyToMailId: this.replyTo()! } : {}),
          ...(this.forward() ? { forwardOfMailId: this.forward()! } : {}),
        },
      }),
    {
      successToastKey: 'mailboxes.compose.sent',
      onSuccess: () => {
        this.store.bump();
        void this.router.navigate(['/mailboxes', this.mailboxId(), 'sent']);
      },
    },
  );
}
