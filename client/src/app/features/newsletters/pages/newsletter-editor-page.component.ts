import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { ProgressBar } from '@openng/optimus-ui/progressbar';
import { Textarea } from '@openng/optimus-ui/textarea';
import { Subscription, interval } from 'rxjs';

import { Newsletter, NewsletterKind, NewsletterProgress, NewsletterStatus, NewslettersService, RecipientCount } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

export const PROGRESS_POLL_MS = 2_000;

/** Percentage of a send run (0 when nothing to send). */
export function progressPercent(progress: NewsletterProgress | undefined): number {
  if (!progress?.total) return 0;
  return Math.min(100, Math.round(((progress.sent + progress.failed) / progress.total) * 100));
}

/**
 * `/newsletters/:newsletterId` (N7): subject + Markdown body with live preview, recipient count, "Send test to me",
 * "Queue" (confirm with the count), progress while QUEUED/SENDING (polled), cancel; read-only once sent. The kind
 * badge and, for info mails (A20), a hint and their own confirmation text tell who receives it.
 */
@Component({
  selector: 'fs-newsletter-editor-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, InputText, MarkdownComponent, PageHeaderComponent, ProgressBar, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'newsletters.editor.title' | t" [backLink]="['/newsletters']" [backLabel]="'newsletters.editor.back' | t" icon="pi pi-megaphone" />
    <fs-data-state [state]="newsletter" skeleton="form" [skeletonRows]="4" data-testid="newsletters.editor.state">
      <ng-template [fsData]="newsletter" let-nl>
        <div class="editor" [attr.data-status]="nl.status" data-testid="newsletters.editor.root">
          <div class="meta">
            <span [class]="'badge badge--' + nl.status" data-testid="newsletters.editor.status">{{ 'newsletters.status.' + nl.status | t }}</span>
            <span [class]="'badge badge--' + (nl.kind ?? 'NEWSLETTER')" data-testid="newsletters.editor.kind" [attr.data-kind]="nl.kind ?? 'NEWSLETTER'">{{
              'newsletters.kind.' + (nl.kind ?? 'NEWSLETTER') | t
            }}</span>
            <span class="muted">{{ nl.region?.name ?? ('newsletters.list.global' | t) }}</span>
            @if (count.data(); as c) {
              <span class="muted" data-testid="newsletters.editor.recipients" [attr.data-count]="c.count">{{ 'newsletters.list.recipients' | t: { count: c.count } }}</span>
            }
          </div>

          @if (nl.kind === 'INFO_MAIL') {
            <p class="kind-hint" data-testid="newsletters.editor.infoMailHint">
              <i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'newsletters.editor.infoMailHint' | t }}
            </p>
          }
          @if (editable()) {
            <div class="field">
              <label for="nl-subject">{{ 'newsletters.editor.subject' | t }}</label>
              <input pInputText id="nl-subject" [(ngModel)]="subject" maxlength="200" data-testid="newsletters.editor.subject" />
            </div>
            <div class="columns">
              <div class="field">
                <label for="nl-body">{{ 'newsletters.editor.body' | t }}</label>
                <textarea pTextarea id="nl-body" rows="14" [(ngModel)]="body" data-testid="newsletters.editor.body"></textarea>
                <small class="muted">{{ 'newsletters.editor.markdownHint' | t }}</small>
              </div>
              <section class="preview fs-card" [attr.aria-label]="'newsletters.editor.preview' | t" data-testid="newsletters.editor.preview">
                <h2>{{ subject() || ('newsletters.list.untitled' | t) }}</h2>
                <fs-markdown [markdown]="body()" />
              </section>
            </div>
            <div class="actions">
              <p-button [label]="'common.save' | t" icon="pi pi-save" [loading]="save.busy()" [disabled]="!dirty()" (onClick)="save.run()" data-testid="newsletters.editor.save" />
              <p-button [label]="'newsletters.editor.test' | t" icon="pi pi-envelope" [outlined]="true" [loading]="test.busy()" (onClick)="sendTest()" data-testid="newsletters.editor.test" />
              <p-button [label]="'newsletters.editor.queue' | t" icon="pi pi-send" severity="success" [disabled]="!subject().trim() || !body().trim()" [loading]="queue.busy()" (onClick)="askQueue()" data-testid="newsletters.editor.queue" />
              <p-button [label]="'newsletters.editor.delete' | t" icon="pi pi-trash" severity="danger" [text]="true" [loading]="remove.busy()" (onClick)="askDelete()" data-testid="newsletters.editor.delete" />
            </div>
          } @else {
            <section class="preview fs-card" data-testid="newsletters.editor.preview">
              <h2>{{ nl.subject }}</h2>
              <fs-markdown [markdown]="nl.bodyMarkdown" />
            </section>
          }

          @if (progress(); as p) {
            <section class="fs-card progress" data-testid="newsletters.editor.progress" [attr.data-status]="p.status">
              <p-progressbar [value]="percent()" [attr.aria-label]="'newsletters.editor.progress' | t" />
              <p>{{ 'newsletters.editor.progressText' | t: { sent: p.sent, failed: p.failed, total: p.total } }}</p>
              @if (p.status === 'QUEUED' || p.status === 'SENDING') {
                <p-button [label]="'newsletters.editor.cancel' | t" icon="pi pi-stop" severity="danger" [outlined]="true" [loading]="cancel.busy()" (onClick)="cancel.run()" data-testid="newsletters.editor.cancel" />
              }
            </section>
          }
          @if (nl.sentAt) {
            <p class="muted">{{ 'newsletters.editor.sentAt' | t: { time: (nl.sentAt | fsDate: 'dateTime') } }}</p>
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../newsletters.scss',
})
export class NewsletterEditorPageComponent {
  private readonly api = inject(NewslettersService);
  private readonly i18n = inject(I18nService);
  private readonly confirm = inject(ConfirmationService);
  private readonly router = inject(Router);

  readonly newsletterId = input.required<string>();

  protected readonly subject = signal('');
  protected readonly body = signal('');
  protected readonly progress = signal<NewsletterProgress | undefined>(undefined);
  protected readonly percent = computed(() => progressPercent(this.progress()));
  private poll?: Subscription;

  protected readonly newsletter = loadResource<Newsletter, string>({
    params: () => this.newsletterId(),
    loader: (newsletterId) => this.api.getNewsletter({ newsletterId }),
    isEmpty: () => false,
  });

  protected readonly count = loadResource<RecipientCount, string>({
    params: () => this.newsletterId(),
    loader: (newsletterId) => this.api.countNewsletterRecipients({ newsletterId }),
    isEmpty: () => false,
  });

  protected readonly editable = computed(() => this.newsletter.data()?.status === NewsletterStatus.Draft);
  protected readonly dirty = computed(() => {
    const nl = this.newsletter.data();
    return !!nl && (nl.subject !== this.subject() || nl.bodyMarkdown !== this.body());
  });

  constructor() {
    effect(() => {
      const nl = this.newsletter.data();
      if (!nl) return;
      untracked(() => {
        this.subject.set(nl.subject);
        this.body.set(nl.bodyMarkdown);
        if (nl.status === NewsletterStatus.Draft) {
          this.stopPolling();
        } else {
          this.startPolling();
        }
      });
    });
    inject(DestroyRef).onDestroy(() => this.stopPolling());
  }

  protected readonly save = runMutation(
    () =>
      this.api.updateNewsletter({
        newsletterId: this.newsletterId(),
        newsletterPatch: { subject: this.subject().trim(), bodyMarkdown: this.body() },
      }),
    { successToastKey: 'newsletters.editor.saved', onSuccess: () => this.newsletter.reload() },
  );

  protected readonly test = runMutation(() => this.api.sendNewsletterTest({ newsletterId: this.newsletterId() }), {
    successToastKey: 'newsletters.editor.testSent',
  });

  protected readonly queue = runMutation(() => this.api.queueNewsletter({ newsletterId: this.newsletterId() }), {
    successToastKey: 'newsletters.editor.queued',
    onSuccess: () => this.newsletter.reload(),
  });

  protected readonly cancel = runMutation(() => this.api.cancelNewsletter({ newsletterId: this.newsletterId() }), {
    successToastKey: 'newsletters.editor.cancelled',
    onSuccess: () => this.newsletter.reload(),
  });

  protected readonly remove = runMutation(() => this.api.deleteNewsletter({ newsletterId: this.newsletterId() }), {
    successToastKey: 'newsletters.editor.deleted',
    onSuccess: () => void this.router.navigate(['/newsletters']),
  });

  private readonly loadProgress = runMutation(() => this.api.getNewsletterProgress({ newsletterId: this.newsletterId() }), {
    errorToast: false,
    onSuccess: (progress) => {
      this.progress.set(progress);
      if (progress.status !== NewsletterStatus.Queued && progress.status !== NewsletterStatus.Sending) {
        this.stopPolling();
        if (this.newsletter.data()?.status !== progress.status) this.newsletter.reload();
      }
    },
  });

  /** Saves pending edits first so the test mail shows what is on screen. */
  protected async sendTest(): Promise<void> {
    if (this.dirty() && !(await this.save.run())) return;
    void this.test.run();
  }

  protected askQueue(): void {
    const count = this.count.data()?.count ?? 0;
    const infoMail = this.newsletter.data()?.kind === NewsletterKind.InfoMail;
    this.confirm.confirm({
      header: this.i18n.t(infoMail ? 'newsletters.editor.queueTitleInfoMail' : 'newsletters.editor.queueTitle'),
      message: this.i18n.t(infoMail ? 'newsletters.editor.queueTextInfoMail' : 'newsletters.editor.queueText', { count }),
      acceptLabel: this.i18n.t('newsletters.editor.queue'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () =>
        void (async () => {
          if (this.dirty() && !(await this.save.run())) return;
          await this.queue.run();
        })(),
    });
  }

  protected askDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('newsletters.editor.deleteTitle'),
      message: this.i18n.t('newsletters.editor.deleteText'),
      acceptLabel: this.i18n.t('newsletters.editor.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }

  private startPolling(): void {
    void this.loadProgress.run();
    if (!this.poll) {
      this.poll = interval(PROGRESS_POLL_MS).subscribe(() => void this.loadProgress.run());
    }
  }

  private stopPolling(): void {
    this.poll?.unsubscribe();
    this.poll = undefined;
  }
}
