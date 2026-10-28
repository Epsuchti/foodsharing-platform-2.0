import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { ContentPage, Language, OnboardingService, PublicContentService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * /learn/:key (C1, onboarding step 3): one learning chapter with reading time. Signed-in users mark it as read
 * (`markChapterRead`, counts towards the journey) and continue with the next chapter; guests just read on.
 */
@Component({
  selector: 'fs-learning-chapter-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    MarkdownComponent,
    PageHeaderComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state
      [state]="chapter"
      skeleton="detail"
      backLink="/learn"
      data-testid="content.chapter.state"
    >
      <ng-template [fsData]="chapter" let-c>
        <fs-page-header
          [eyebrow]="'content.chapter.eyebrow' | t"
          [title]="c.title"
          backLink="/learn"
          [backLabel]="'content.chapter.back' | t"
        />
        <article
          class="fs-card chapter"
          [attr.data-key]="key()"
          data-testid="content.chapter.article"
        >
          <p class="chapter__meta">
            @if (c.estimatedMinutes) {
              <span data-testid="content.chapter.minutes">
                <i class="pi pi-clock" aria-hidden="true"></i>
                {{ 'content.chapter.readingTime' | t: { count: c.estimatedMinutes } }}
              </span>
            }
            @if (c.fallback) {
              <span data-testid="content.chapter.fallback">
                <i class="pi pi-language" aria-hidden="true"></i>{{ 'content.page.fallback' | t }}
              </span>
            }
          </p>
          <fs-markdown [markdown]="c.body" data-testid="content.chapter.body" />
        </article>

        <footer class="next" data-testid="content.chapter.actions">
          @if (signedIn()) {
            @if (markedRead()) {
              <p class="next__done" role="status" data-testid="content.chapter.done">
                <i class="pi pi-check-circle" aria-hidden="true"></i
                >{{ 'content.chapter.markedRead' | t }}
              </p>
              <p-button
                [label]="
                  (c.nextChapterKey ? 'content.chapter.nextChapter' : 'content.chapter.toOverview')
                    | t
                "
                icon="pi pi-arrow-right"
                iconPos="right"
                (onClick)="continue(c)"
                data-testid="content.chapter.next"
              />
            } @else {
              <p class="next__text">{{ 'content.chapter.markHint' | t }}</p>
              <p-button
                [label]="
                  (c.nextChapterKey ? 'content.chapter.markReadNext' : 'content.chapter.markRead')
                    | t
                "
                icon="pi pi-check"
                [loading]="markRead.busy()"
                (onClick)="markRead.run()"
                data-testid="content.chapter.markRead"
              />
            }
          } @else {
            <p class="next__text">{{ 'content.learn.signInHint' | t }}</p>
            @if (c.nextChapterKey) {
              <p-button
                [label]="'content.chapter.nextChapter' | t"
                icon="pi pi-arrow-right"
                iconPos="right"
                [routerLink]="['/learn', c.nextChapterKey]"
                data-testid="content.chapter.next"
              />
            }
          }
        </footer>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .chapter {
      display: grid;
      gap: 1rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2.25rem);
      font-size: 1.02rem;
    }
    .chapter__meta {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem 1rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .chapter__meta span {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
    .next {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem 1rem;
      margin-top: 1.25rem;
      padding: 1rem 1.25rem;
      border: 1px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-lg);
    }
    .next__text,
    .next__done {
      flex: 1 1 16rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.92rem;
    }
    .next__done {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class LearningChapterPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly onboardingApi = inject(OnboardingService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  /** Route parameter. */
  readonly key = input.required<string>();

  protected readonly signedIn = this.session.isAuthenticated;
  protected readonly markedRead = signal(false);

  protected readonly chapter = loadResource<ContentPage, { key: string; lang: string }>({
    params: () => ({ key: this.key(), lang: this.i18n.language() }),
    loader: ({ key, lang }) => {
      this.markedRead.set(false);
      return this.api.getLearningChapter({ key, lang: lang as Language });
    },
  });

  /** Marks the chapter as read; with a next chapter the reader moves on right away. */
  protected readonly markRead = runMutation(
    () => this.onboardingApi.markChapterRead({ key: this.key() }),
    {
      successToastKey: 'content.chapter.markedRead',
      onSuccess: () => {
        const next = this.chapter.data()?.nextChapterKey;
        if (next) {
          void this.router.navigate(['/learn', next]);
        } else {
          this.markedRead.set(true);
        }
      },
    },
  );

  protected continue(chapter: ContentPage): void {
    void this.router.navigate(
      chapter.nextChapterKey ? ['/learn', chapter.nextChapterKey] : ['/learn'],
    );
  }
}
