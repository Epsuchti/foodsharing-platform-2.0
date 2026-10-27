import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { ApiErrorView, errorParams } from '../../core/data/api-error';
import { statusForError } from '../../core/data/data-state';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';

export type ErrorKind = 'error' | 'forbidden' | 'notFound' | 'offline';

const ICONS: Record<ErrorKind, string> = {
  error: 'pi pi-exclamation-triangle',
  forbidden: 'pi pi-lock',
  notFound: 'pi pi-map',
  offline: 'pi pi-wifi',
};

/**
 * Translated error with retry and a "details" expander (trace id + copy) for support.
 * Variants: error, forbidden (with "who to ask"), notFound (with back link), offline.
 */
@Component({
  selector: 'fs-error-state',
  imports: [Button, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-error-state',
    role: 'alert',
    '[class.fs-error-state--compact]': 'compact()',
    '[attr.data-kind]': 'kindValue()',
  },
  template: `
    <div class="err__icon" [attr.data-kind]="kindValue()" aria-hidden="true">
      <i [class]="icon()"></i>
    </div>
    <div class="err__body">
      <h3 class="err__title">{{ title() }}</h3>
      <p class="err__text">{{ text() }}</p>
      @if (askWho() && kindValue() === 'forbidden') {
        <p class="err__ask"><i class="pi pi-user" aria-hidden="true"></i> {{ askWho() }}</p>
      }
      <div class="err__actions">
        @if (showRetry()) {
          <p-button
            [label]="'shared.errorState.retry' | t"
            icon="pi pi-refresh"
            [size]="compact() ? 'small' : undefined"
            (onClick)="retry.emit()"
            [attr.data-testid]="(testId() ?? 'shared.errorState') + '.retry'"
          />
        }
        @if (backLink() && (kindValue() === 'notFound' || kindValue() === 'forbidden')) {
          <a
            class="fs-btn fs-btn--outlined"
            [class.fs-btn--sm]="compact()"
            [routerLink]="backLink()"
            [attr.data-testid]="(testId() ?? 'shared.errorState') + '.back'"
          >
            <i class="pi pi-arrow-left" aria-hidden="true"></i>
            <span>{{ backLabel() ?? ('shared.errorState.back' | t) }}</span>
          </a>
        }
        @if (error()?.traceId) {
          <button
            type="button"
            class="err__details-toggle"
            [attr.aria-expanded]="detailsOpen()"
            (click)="detailsOpen.set(!detailsOpen())"
            [attr.data-testid]="(testId() ?? 'shared.errorState') + '.details'"
          >
            {{ 'shared.errorState.details' | t }}
            <i
              class="pi"
              [class.pi-chevron-down]="!detailsOpen()"
              [class.pi-chevron-up]="detailsOpen()"
              aria-hidden="true"
            ></i>
          </button>
        }
      </div>
      @if (detailsOpen() && error(); as err) {
        <dl class="err__details">
          <dt>{{ 'shared.errorState.traceId' | t }}</dt>
          <dd>
            <code data-testid="shared.errorState.traceId">{{ err.traceId }}</code>
            <button
              type="button"
              class="err__copy"
              (click)="copy(err.traceId!)"
              [attr.aria-label]="'shared.errorState.copy' | t"
            >
              <i
                class="pi"
                [class.pi-copy]="!copied()"
                [class.pi-check]="copied()"
                aria-hidden="true"
              ></i>
              {{ copied() ? ('shared.errorState.copied' | t) : ('shared.errorState.copy' | t) }}
            </button>
          </dd>
          <dt>{{ 'shared.errorState.code' | t }}</dt>
          <dd>
            <code>{{ err.code }}{{ err.status > 0 ? ' · HTTP ' + err.status : '' }}</code>
          </dd>
        </dl>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      gap: 1rem;
      padding: 1.5rem;
      border: 1px solid var(--fs-danger-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-danger-soft);
    }
    :host([data-kind='forbidden']),
    :host([data-kind='notFound']) {
      border-color: var(--fs-border);
      background: var(--fs-surface-muted);
    }
    :host([data-kind='offline']) {
      border-color: var(--fs-warning-border);
      background: var(--fs-warning-soft);
    }
    :host(.fs-error-state--compact) {
      padding: 0.85rem 1rem;
      gap: 0.75rem;
    }
    .err__icon {
      display: grid;
      flex: 0 0 auto;
      width: 3rem;
      height: 3rem;
      place-items: center;
      border-radius: 50%;
      background: var(--fs-surface);
      color: var(--fs-danger);
      font-size: 1.25rem;
      box-shadow: var(--fs-shadow-sm);
    }
    .err__icon[data-kind='forbidden'],
    .err__icon[data-kind='notFound'] {
      color: var(--fs-text-muted);
    }
    .err__icon[data-kind='offline'] {
      color: var(--fs-warning);
    }
    :host(.fs-error-state--compact) .err__icon {
      width: 2.25rem;
      height: 2.25rem;
      font-size: 1rem;
    }
    .err__body {
      display: grid;
      min-width: 0;
      flex: 1;
      gap: 0.35rem;
    }
    .err__title {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 700;
    }
    :host(.fs-error-state--compact) .err__title {
      font-size: 0.95rem;
    }
    .err__text,
    .err__ask {
      margin: 0;
      color: var(--fs-text-muted);
    }
    .err__ask {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-weight: 600;
      color: var(--fs-text);
    }
    .err__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.6rem 1rem;
      margin-top: 0.6rem;
    }
    .err__actions a {
      gap: 0.5rem;
      text-decoration: none;
    }
    .err__details-toggle,
    .err__copy {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.3rem 0.1rem;
      border: 0;
      background: none;
      color: var(--fs-text-muted);
      font: inherit;
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
    }
    .err__details-toggle:hover,
    .err__copy:hover {
      color: var(--fs-text);
    }
    .err__details {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.35rem 1rem;
      margin: 0.5rem 0 0;
      padding: 0.75rem 0.9rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-surface);
      font-size: 0.85rem;
    }
    .err__details dt {
      color: var(--fs-text-muted);
      font-weight: 600;
    }
    .err__details dd {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      min-width: 0;
    }
    .err__details code {
      overflow-wrap: anywhere;
    }
  `,
})
export class ErrorStateComponent {
  private readonly i18n = inject(I18nService);

  readonly error = input<ApiErrorView | undefined>(undefined);
  /** Overrides the variant derived from the error status. */
  readonly kind = input<ErrorKind | undefined>(undefined);
  /** Retry button: true/false, or undefined = shown when the error is retryable (or kind error/offline). */
  readonly retryable = input<boolean | undefined>(undefined);
  /** Translated "who to ask" hint for the forbidden variant (e.g. "Ask the store manager"). */
  readonly askWho = input<string | undefined>(undefined);
  /** Back link for notFound/forbidden (default none). */
  readonly backLink = input<string | unknown[] | undefined>(undefined);
  readonly backLabel = input<string | undefined>(undefined);
  /** Custom title/text (translated) instead of the defaults. */
  readonly heading = input<string | undefined>(undefined);
  readonly description = input<string | undefined>(undefined);
  readonly compact = input(false);
  readonly testId = input<string | undefined>(undefined);
  readonly retry = output<void>();

  protected readonly detailsOpen = signal(false);
  protected readonly copied = signal(false);

  protected readonly kindValue = computed<ErrorKind>(() => {
    const explicit = this.kind();
    if (explicit) {
      return explicit;
    }
    const error = this.error();
    const status = error ? statusForError(error) : 'error';
    return status === 'forbidden' || status === 'notFound' || status === 'offline'
      ? status
      : 'error';
  });

  protected readonly icon = computed(() => ICONS[this.kindValue()]);

  protected readonly title = computed(() => {
    if (this.heading()) {
      return this.heading()!;
    }
    switch (this.kindValue()) {
      case 'forbidden':
        return this.i18n.t('shared.errorState.title.forbidden');
      case 'notFound':
        return this.i18n.t('shared.errorState.title.notFound');
      case 'offline':
        return this.i18n.t('shared.errorState.title.offline');
      default:
        return this.i18n.t('shared.errorState.title.error');
    }
  });

  protected readonly text = computed(() => {
    if (this.description()) {
      return this.description()!;
    }
    const error = this.error();
    switch (this.kindValue()) {
      case 'offline':
        return this.i18n.t('shared.errorState.text.offline');
      case 'forbidden':
        return this.i18n.t('shared.errorState.text.forbidden');
      case 'notFound':
        return this.i18n.t('shared.errorState.text.notFound');
      default:
        return error
          ? this.i18n.error(error.code, errorParams(error))
          : this.i18n.t('errors.UNKNOWN');
    }
  });

  protected readonly showRetry = computed(() => {
    const explicit = this.retryable();
    if (explicit !== undefined) {
      return explicit;
    }
    const kind = this.kindValue();
    return kind === 'offline' || (kind === 'error' && (this.error()?.retryable ?? true));
  });

  protected copy(text: string): void {
    void globalThis.navigator?.clipboard?.writeText(text).then(
      () => {
        this.copied.set(true);
        setTimeout(() => this.copied.set(false), 2000);
      },
      () => undefined,
    );
  }
}
