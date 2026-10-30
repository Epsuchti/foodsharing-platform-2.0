import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { ProgressBarComponent } from '../../../shared/progress-bar/progress-bar.component';

import {
  Journey,
  Language,
  LearningChapter,
  OnboardingService,
  PublicContentService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { LearningChapterItemComponent } from '../components/learning-chapter-item.component';
import { chapterState, learningProgress } from '../learning-progress';

/**
 * /learn (C1, onboarding step 3): the learning chapters in order with reading time; signed-in users see how many they
 * have read and continue with the first unread one. Public, so newcomers can read before signing up.
 */
@Component({
  selector: 'fs-learning-overview-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    LearningChapterItemComponent,
    PageHeaderComponent,
    ProgressBarComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [eyebrow]="'content.learn.eyebrow' | t"
      [title]="'content.learn.title' | t"
      [subtitle]="'content.learn.subtitle' | t"
      icon="pi pi-book"
    />

    @if (signedIn()) {
      <fs-data-state
        [state]="journey"
        [compact]="true"
        skeleton="card"
        data-testid="content.learn.progressState"
      >
        <ng-template [fsData]="journey">
          @if (progress(); as p) {
            <section
              class="fs-card progress"
              data-testid="content.learn.progress"
              [attr.data-read]="p.read"
            >
              <div class="progress__text">
                <strong>
                  @if (p.done) {
                    {{ 'content.learn.allRead' | t }}
                  } @else {
                    {{ 'content.learn.progress' | t: { read: p.read, total: p.total } }}
                  }
                </strong>
                <span>{{
                  (p.optional ? 'content.learn.optional' : 'content.learn.required') | t
                }}</span>
              </div>
              <fs-progress-bar
                [value]="p.total ? (p.read * 100) / p.total : 0"
                [label]="'content.learn.progress' | t: { read: p.read, total: p.total }"
              />
              @if (p.nextKey) {
                <p-button
                  [label]="(p.read ? 'content.learn.continue' : 'content.learn.start') | t"
                  icon="pi pi-arrow-right"
                  iconPos="right"
                  [routerLink]="['/learn', p.nextKey]"
                  data-testid="content.learn.continue"
                />
              } @else if (p.done) {
                <p-button
                  [label]="'content.learn.toJourney' | t"
                  icon="pi pi-map"
                  severity="secondary"
                  routerLink="/onboarding"
                  data-testid="content.learn.toJourney"
                />
              }
            </section>
          }
        </ng-template>
      </fs-data-state>
    }

    <fs-data-state
      [state]="chapters"
      skeleton="list"
      emptyIcon="pi pi-book"
      [emptyTitle]="'content.learn.empty' | t"
      data-testid="content.learn.state"
    >
      <ng-template [fsData]="chapters" let-list>
        <ol class="chapters">
          @for (chapter of list; track chapter.key; let i = $index) {
            <li>
              <fs-learning-chapter-item
                [chapter]="chapter"
                [position]="i + 1"
                [state]="state(chapter.key)"
              />
            </li>
          }
        </ol>
        @if (!signedIn()) {
          <p class="hint">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            {{ 'content.learn.signInHint' | t }}
            <a routerLink="/login" data-testid="content.learn.login">{{
              'content.learn.signIn' | t
            }}</a>
          </p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .progress {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      align-items: center;
      gap: 0.75rem 1.25rem;
      margin-bottom: 1.25rem;
      padding: 1.1rem 1.25rem;
    }
    .progress__text {
      display: grid;
      gap: 0.15rem;
    }
    .progress__text span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .progress fs-progress-bar {
      grid-column: 1 / -1;
      grid-row: 2;
      --p-progressbar-height: 0.5rem;
    }
    .chapters {
      display: grid;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .hint {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.4rem;
      margin: 1.25rem 0 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .hint a {
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class LearningOverviewPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly onboardingApi = inject(OnboardingService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  protected readonly signedIn = this.session.isAuthenticated;

  protected readonly chapters = loadResource<LearningChapter[], string>({
    params: () => this.i18n.language(),
    loader: (lang) => this.api.listLearningChapters({ lang: lang as Language }),
  });

  protected readonly journey = loadResource<Journey, true | undefined>({
    params: () => (this.signedIn() ? true : undefined),
    loader: () => this.onboardingApi.getMyJourney(),
  });

  protected readonly progress = computed(() =>
    learningProgress(
      this.journey.data(),
      (this.chapters.data() ?? []).map((chapter) => chapter.key),
    ),
  );

  protected state(key: string) {
    return chapterState(this.progress(), key);
  }
}
