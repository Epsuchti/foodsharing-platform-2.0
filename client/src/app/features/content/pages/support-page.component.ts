import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';
import { Observable } from 'rxjs';

import {
  ApiErrorCode,
  PublicSupportService,
  SupportService,
  SupportTicketCreated,
  Upload,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { ApiErrorView, errorParams } from '../../../core/data/api-error';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UploadDropzoneComponent } from '../../../shared/upload-dropzone/upload-dropzone.component';
import { SUPPORT_LIMITS, plausibleEmail, supportSourcePage } from '../support.util';

/**
 * /support (C9, SUPPORT_FORM toggle): subject and message, the page the request is about (`?from=` or the referrer)
 * and the browser's user agent are sent along read-only. Signed in: `createSupportTicket` with up to five
 * attachments (`fs-upload-dropzone`); logged out: `createPublicSupportTicket` with first name and email, no
 * attachments. 429 SUPPORT_RATE_LIMITED shows its own notice; the form keeps its content.
 */
@Component({
  selector: 'fs-support-page',
  imports: [
    Button,
    FormsModule,
    InputText,
    PageHeaderComponent,
    RouterLink,
    Textarea,
    TPipe,
    UploadDropzoneComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.support.title' | t"
      [subtitle]="'content.support.subtitle' | t"
      icon="pi pi-question-circle"
    />

    @if (sent(); as ticket) {
      <section class="fs-card done" role="status" data-testid="content.support.sent">
        <i class="pi pi-check-circle" aria-hidden="true"></i>
        <h2>{{ 'content.support.sentTitle' | t }}</h2>
        <p>{{ 'content.support.sentText' | t }}</p>
        <p class="done__id">
          {{ 'content.support.ticketId' | t }}
          <code data-testid="content.support.ticketId">{{ ticket.ticketId }}</code>
        </p>
        <div class="done__actions">
          <p-button
            [label]="'content.support.another' | t"
            icon="pi pi-plus"
            severity="secondary"
            [outlined]="true"
            (onClick)="reset()"
            data-testid="content.support.another"
          />
          <p-button [label]="'content.support.faq' | t" icon="pi pi-comments" [text]="true" routerLink="/faq" />
        </div>
      </section>
    } @else {
      <p class="intro">
        {{ 'content.support.intro' | t }}
        <a routerLink="/faq" data-testid="content.support.faqLink">{{ 'content.support.faqLink' | t }}</a>
      </p>

      @if (rateLimited()) {
        <p class="notice notice--warn" role="alert" data-testid="content.support.rateLimited">
          <i class="pi pi-clock" aria-hidden="true"></i>{{ 'content.support.rateLimited' | t }}
        </p>
      } @else if (errorText(); as message) {
        <p class="notice notice--error" role="alert" data-testid="content.support.error">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>{{ message }}
        </p>
      }

      <form class="fs-card form" (ngSubmit)="submit()" data-testid="content.support.form">
        @if (!loggedIn()) {
          <div class="row">
            <div class="field">
              <label for="support-first-name">{{ 'content.support.firstName' | t }}</label>
              <input
                pInputText
                id="support-first-name"
                name="firstName"
                autocomplete="given-name"
                maxlength="100"
                required
                [ngModel]="firstName()"
                (ngModelChange)="firstName.set($event)"
                data-testid="content.support.firstName"
              />
            </div>
            <div class="field">
              <label for="support-email">{{ 'content.support.email' | t }}</label>
              <input
                pInputText
                id="support-email"
                name="email"
                type="email"
                autocomplete="email"
                maxlength="320"
                required
                [attr.aria-invalid]="!!email() && !emailValid()"
                [ngModel]="email()"
                (ngModelChange)="email.set($event)"
                data-testid="content.support.email"
              />
            </div>
          </div>
        }
        <div class="field">
          <label for="support-subject">{{ 'content.support.subject' | t }}</label>
          <input
            pInputText
            id="support-subject"
            name="subject"
            [maxlength]="limits.subject"
            required
            [ngModel]="subject()"
            (ngModelChange)="subject.set($event)"
            data-testid="content.support.subject"
          />
        </div>
        <div class="field">
          <label for="support-body">{{ 'content.support.body' | t }}</label>
          <textarea
            pTextarea
            id="support-body"
            name="body"
            rows="8"
            [maxlength]="limits.body"
            required
            [ngModel]="body()"
            (ngModelChange)="body.set($event)"
            data-testid="content.support.body"
          ></textarea>
        </div>

        @if (loggedIn()) {
          <div class="field">
            <span class="field__label">{{ 'content.support.attachments' | t }}</span>
            <fs-upload-dropzone
              [(uploads)]="attachments"
              [maxFiles]="limits.attachments"
              accept="image/*,application/pdf"
              testId="content.support.files"
            />
          </div>
        }

        <fieldset class="context">
          <legend>{{ 'content.support.context' | t }}</legend>
          <div class="field">
            <label for="support-page">{{ 'content.support.page' | t }}</label>
            <input
              pInputText
              id="support-page"
              name="page"
              readonly
              [value]="sourcePage() || ('content.support.noPage' | t)"
              data-testid="content.support.page"
            />
          </div>
          <div class="field">
            <label for="support-ua">{{ 'content.support.userAgent' | t }}</label>
            <input
              pInputText
              id="support-ua"
              name="userAgent"
              readonly
              [value]="userAgent"
              data-testid="content.support.userAgent"
            />
          </div>
          <small>{{ 'content.support.contextHint' | t }}</small>
        </fieldset>

        <div class="actions">
          <p-button
            type="submit"
            [label]="'content.support.send' | t"
            icon="pi pi-send"
            [disabled]="!valid()"
            [loading]="send.busy()"
            data-testid="content.support.send"
          />
        </div>
      </form>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 1rem;
      width: min(100%, 44rem);
      margin: 0 auto;
    }
    .intro {
      margin: 0;
      color: var(--fs-text-muted);
    }
    .form {
      display: grid;
      gap: 1.1rem;
      padding: clamp(1rem, 0.7rem + 1.2vw, 1.75rem);
    }
    .row {
      display: grid;
      gap: 1.1rem;
      @media (min-width: 36rem) {
        grid-template-columns: 1fr 1fr;
      }
    }
    .field {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .field label,
    .field__label {
      font-weight: 600;
    }
    .field :is(input, textarea) {
      width: 100%;
    }
    .context {
      display: grid;
      gap: 0.75rem;
      margin: 0;
      padding: 0.85rem 1rem 1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .context legend {
      padding: 0 0.35rem;
      font-weight: 600;
    }
    .context input {
      color: var(--fs-text-muted);
    }
    .context small {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
    .notice {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      padding: 0.7rem 0.9rem;
      border-radius: var(--fs-radius-md);
    }
    .notice--warn {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .notice--error {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .done {
      display: grid;
      justify-items: center;
      gap: 0.6rem;
      padding: 2rem 1.25rem;
      text-align: center;
    }
    .done > i {
      color: var(--fs-primary);
      font-size: 2.5rem;
    }
    .done h2,
    .done p {
      margin: 0;
    }
    .done__id {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .done__actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 0.5rem;
    }
  `,
})
export class SupportPageComponent {
  private readonly supportApi = inject(SupportService);
  private readonly publicApi = inject(PublicSupportService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  /** Query parameter: the app path the request is about. */
  readonly from = input<string | undefined>(undefined);

  protected readonly limits = SUPPORT_LIMITS;
  protected readonly loggedIn = this.session.isAuthenticated;
  protected readonly userAgent = (this.document.defaultView?.navigator.userAgent ?? '').slice(
    0,
    SUPPORT_LIMITS.userAgent,
  );
  protected readonly sourcePage = computed(() =>
    supportSourcePage(
      this.from(),
      this.document.referrer,
      this.document.defaultView?.location.origin ?? '',
    ),
  );

  protected readonly subject = signal('');
  protected readonly body = signal('');
  protected readonly firstName = signal('');
  protected readonly email = signal('');
  protected readonly attachments = signal<Upload[]>([]);
  protected readonly sent = signal<SupportTicketCreated | null>(null);
  private readonly failure = signal<ApiErrorView | null>(null);

  protected readonly emailValid = computed(() => plausibleEmail(this.email()));
  protected readonly valid = computed(
    () =>
      !!this.subject().trim() &&
      !!this.body().trim() &&
      (this.loggedIn() || (!!this.firstName().trim() && this.emailValid())),
  );
  protected readonly rateLimited = computed(
    () => this.failure()?.code === ApiErrorCode.SupportRateLimited,
  );
  protected readonly errorText = computed(() => {
    const error = this.failure();
    return error ? this.i18n.error(error.code, errorParams(error)) : null;
  });

  protected readonly send = runMutation(
    (): Observable<SupportTicketCreated> => {
      const common = {
        subject: this.subject().trim(),
        body: this.body().trim(),
        page: this.sourcePage() || undefined,
        userAgent: this.userAgent || undefined,
      };
      return this.loggedIn()
        ? this.supportApi.createSupportTicket({
            supportTicketCreate: {
              ...common,
              attachmentUploadIds: this.attachments().map((upload) => upload.id),
            },
          })
        : this.publicApi.createPublicSupportTicket({
            publicSupportTicketCreate: {
              ...common,
              firstName: this.firstName().trim(),
              email: this.email().trim(),
            },
          });
    },
    {
      errorToast: false,
      onError: (error) => this.failure.set(error),
      onSuccess: (ticket) => this.sent.set(ticket),
    },
  );

  protected submit(): void {
    if (!this.valid()) {
      return;
    }
    this.failure.set(null);
    void this.send.run();
  }

  protected reset(): void {
    this.subject.set('');
    this.body.set('');
    this.attachments.set([]);
    this.failure.set(null);
    this.sent.set(null);
  }
}
