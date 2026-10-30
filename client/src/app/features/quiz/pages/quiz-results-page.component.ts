import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { map, of, switchMap, tap } from 'rxjs';

import {
  AuthService,
  Language,
  LegalDocument,
  LegalDocumentKind,
  LegalService,
  PublicLegalService,
  QuizKind,
  QuizResult,
  QuizzesService,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { QuizSolutionListComponent } from '../components/quiz-solution-list.component';
import { quizKindOf } from '../quiz-kind';

/**
 * /quizzes/:kind/results (Q2): pass / fail with failure points, the next-try rule, a per-question review and — for
 * a passed foodsaver or store manager quiz — the confirmation step: the legal agreement (foodsaver) or the
 * confidentiality notice (store manager) is accepted, then `confirmQuiz` raises the role.
 */
@Component({
  selector: 'fs-quiz-results-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    FormsModule,
    MarkdownComponent,
    PageHeaderComponent,
    QuizSolutionListComponent,
    RouterLink,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="narrow">
      <fs-page-header
        [title]="'quiz.results.title' | t"
        [eyebrow]="(isPractice() ? 'quiz.intro.practiceEyebrow' : 'quiz.intro.eyebrow') | t"
        icon="pi pi-chart-bar"
        [backLink]="['/quizzes', kind()]"
        [backLabel]="'quiz.results.back' | t"
      />
      <fs-data-state [state]="results" skeleton="detail" [backLink]="['/quizzes', kind()]" data-testid="quiz.results.state">
        <ng-template [fsData]="results" let-r>
<div class="stack">
          <section
            class="fs-card hero"
            [class.hero--passed]="r.status === 'PASSED'"
            [attr.data-status]="r.status"
            data-testid="quiz.results.summary"
          >
            <i [class]="r.status === 'PASSED' ? 'pi pi-check-circle' : 'pi pi-times-circle'" aria-hidden="true"></i>
            <div>
              <h2 data-testid="quiz.results.verdict">
                {{ (r.status === 'PASSED' ? 'quiz.results.passed' : 'quiz.results.failed') | t }}
              </h2>
              <p class="muted" data-testid="quiz.results.points">
                {{ 'quiz.results.points' | t: { points: r.failurePoints, max: r.maxFailurePoints } }}
              </p>
            </div>
          </section>

          @if (r.test) {
            <p class="banner banner--info" data-testid="quiz.results.practice">
              <i class="pi pi-info-circle" aria-hidden="true"></i>{{ 'quiz.results.practiceNote' | t }}
            </p>
          } @else if (r.nextTryInfo.disqualified) {
            <p class="banner banner--danger" data-testid="quiz.results.disqualified">
              <i class="pi pi-ban" aria-hidden="true"></i>{{ 'quiz.intro.disqualified' | t }}
            </p>
          } @else if (r.nextTryInfo.waitDays > 0) {
            <p class="banner" data-testid="quiz.results.wait">
              <i class="pi pi-clock" aria-hidden="true"></i>{{ 'quiz.intro.wait' | t: { days: r.nextTryInfo.waitDays } }}
            </p>
          } @else if (r.status === 'FAILED') {
            <p class="banner banner--info" data-testid="quiz.results.retry">
              <i class="pi pi-replay" aria-hidden="true"></i>{{ 'quiz.results.retry' | t }}
            </p>
          }

          @if (confirmed()) {
            <p class="banner banner--success" data-testid="quiz.results.confirmed">
              <i class="pi pi-verified" aria-hidden="true"></i>{{ 'quiz.results.confirmed.' + quizKind() | t }}
            </p>
            @if (journeyOpen()) {
              <!-- design review M7 R3: the success state leads to the next step of the newcomer journey -->
              <div class="actions">
                <p-button
                  [label]="'quiz.results.toJourney' | t"
                  icon="pi pi-arrow-right"
                  iconPos="right"
                  routerLink="/onboarding"
                  data-testid="quiz.results.toJourney"
                />
              </div>
            }
          } @else if (r.requiresConfirmation && r.confirmationDocument) {
            <section class="fs-card card confirm" data-testid="quiz.results.confirm">
              <h2 class="card__title">
                <i class="pi pi-verified" aria-hidden="true"></i>{{ 'quiz.results.confirmTitle.' + r.confirmationDocument | t }}
              </h2>
              <p class="muted">{{ 'quiz.results.confirmText.' + r.confirmationDocument | t }}</p>
              <fs-data-state [state]="document" skeleton="list" [compact]="true" data-testid="quiz.results.document">
                <ng-template [fsData]="document" let-doc>
                  @if (doc.summary) {
                    <div class="doc__summary"><fs-markdown [markdown]="doc.summary" /></div>
                  }
                  <details class="doc__full">
                    <summary>{{ 'quiz.results.fullText' | t }}</summary>
                    <fs-markdown [markdown]="doc.body" />
                  </details>
                  <label class="agree" for="quiz-agree">
                    <p-checkbox inputId="quiz-agree" [binary]="true" [(ngModel)]="agreed" data-testid="quiz.results.agree" />
                    <span>{{ 'quiz.results.agree.' + r.confirmationDocument | t: { version: doc.version } }}</span>
                  </label>
                  <div class="actions">
                    <p-button
                      [label]="'quiz.results.confirmButton' | t"
                      icon="pi pi-check"
                      [disabled]="!agreed()"
                      [loading]="confirm.busy()"
                      (onClick)="confirm.run()"
                      data-testid="quiz.results.confirmButton"
                    />
                  </div>
                </ng-template>
              </fs-data-state>
            </section>
          }

          <section class="card" data-testid="quiz.results.review">
            <h2 class="card__title"><i class="pi pi-list-check" aria-hidden="true"></i>{{ 'quiz.results.review' | t }}</h2>
            <ol class="review">
              @for (question of r.questions; track $index) {
                <li class="fs-card review__item" data-testid="quiz.results.question">
                  <div class="review__head">
                    <h3>{{ question.text }}</h3>
                    <span class="points" [class.points--bad]="question.userFailurePoints > 0">
                      {{ 'quiz.results.questionPoints' | t: { points: question.userFailurePoints } }}
                    </span>
                  </div>
                  @if (question.timedOut) {
                    <p class="banner banner--danger"><i class="pi pi-clock" aria-hidden="true"></i>{{ 'quiz.session.answeredLate' | t }}</p>
                  }
                  <fs-quiz-solution-list [answers]="question.answers" testId="quiz.results.answer" />
                  @if (question.wikiLink) {
                    <a class="wiki" [href]="question.wikiLink" target="_blank" rel="noopener">
                      <i class="pi pi-external-link" aria-hidden="true"></i>{{ 'quiz.session.wiki' | t }}
                    </a>
                  }
                </li>
              }
            </ol>
          </section>

          <div class="actions">
            <p-button
              [label]="'quiz.results.allQuizzes' | t"
              icon="pi pi-arrow-left"
              severity="secondary"
              [outlined]="true"
              routerLink="/quizzes"
              data-testid="quiz.results.allQuizzesButton"
            />
          </div>
        </div>
</ng-template>
      </fs-data-state>
    </div>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .hero {
      display: flex;
      align-items: center;
      gap: 1rem;
      border-color: var(--fs-danger-border);
      background: var(--fs-danger-soft);
    }
    .hero > .pi {
      color: var(--fs-danger);
      font-size: 2.4rem;
    }
    .hero h2 {
      margin: 0 0 0.2rem;
      font-size: 1.35rem;
    }
    .hero--passed {
      border-color: var(--fs-primary-200);
      background: var(--fs-primary-soft);
    }
    .hero--passed > .pi {
      color: var(--fs-primary-strong);
    }
    .confirm {
      border: 2px solid var(--fs-primary);
    }
    .doc__summary {
      padding: 0.75rem 1rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
    }
    .doc__full summary {
      color: var(--fs-primary-strong);
      font-weight: 650;
      cursor: pointer;
    }
    .doc__full fs-markdown {
      display: block;
      max-height: 20rem;
      margin-top: 0.5rem;
      overflow: auto;
    }
    .agree {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      line-height: 1.45;
      cursor: pointer;
    }
    .review {
      display: grid;
      gap: 0.85rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .review__item {
      display: grid;
      gap: 0.75rem;
    }
    .review__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .review__head h3 {
      margin: 0;
      font-size: 1rem;
      line-height: 1.45;
    }
    .points {
      flex: none;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.8rem;
      font-weight: 700;
      white-space: nowrap;
    }
    .points--bad {
      background: var(--fs-danger-soft);
      color: var(--fs-danger);
    }
    .wiki {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      width: fit-content;
      color: var(--fs-primary-strong);
      font-weight: 650;
    }
  `,
})
export class QuizResultsPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly legalApi = inject(LegalService);
  private readonly publicLegalApi = inject(PublicLegalService);
  private readonly authApi = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);

  readonly kind = input.required<string>();
  readonly practice = input<string | undefined>(undefined);

  protected readonly isPractice = computed(() => this.practice() === 'true');
  protected readonly quizKind = computed(() => quizKindOf(this.kind()) as QuizKind);
  protected readonly agreed = signal(false);
  protected readonly confirmed = signal(false);
  /** Newcomers still on their journey continue there after confirming (the quiz is one of its steps). */
  protected readonly journeyOpen = computed(() => this.session.session()?.onboarding?.complete === false);

  protected readonly results = loadResource<QuizResult, { kind: QuizKind; test: boolean }>({
    params: () => ({ kind: this.quizKind(), test: this.isPractice() }),
    loader: ({ kind, test }) => this.api.getQuizResults({ kind, test }),
  });

  /** The document to accept in the confirmation step, in the UI language. */
  protected readonly document = loadResource<LegalDocument, { kind: LegalDocumentKind; lang: string }>({
    params: () => {
      const result = this.results.data();
      return result?.requiresConfirmation && result.confirmationDocument
        ? { kind: result.confirmationDocument, lang: this.i18n.language() }
        : undefined;
    },
    loader: ({ kind, lang }) => this.publicLegalApi.getLegalDocument({ kind, lang: lang as Language }),
  });

  /** Accepts the shown document version unless already accepted, confirms the quiz, then refreshes the session role. */
  protected readonly confirm = runMutation(
    () => {
      const doc = this.document.data()!;
      return this.legalApi.getMyConsents().pipe(
        map((consents) => consents.find((consent) => consent.kind === doc.kind)),
        switchMap((consent) =>
          consent && !consent.pending && consent.acceptedVersion === doc.version
            ? of(null)
            : this.legalApi.acceptLegalDocument({ legalConsentRequest: { kind: doc.kind, version: doc.version } }),
        ),
        switchMap(() => this.api.confirmQuiz({ kind: this.quizKind() })),
        switchMap(() => this.authApi.getSession()),
        tap((info) => this.session.applySession(info)),
      );
    },
    { successToastKey: 'quiz.results.confirmToast', onSuccess: () => this.confirmed.set(true) },
  );
}
