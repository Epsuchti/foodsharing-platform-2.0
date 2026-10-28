import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import { AnswerRating, QuizSessionStatus, QuizStatus } from '../../../../api';
import { QuizSolutionListComponent, SolutionLine } from '../../../quiz/components/quiz-solution-list.component';
import { QuizStatusChipComponent } from '../../../quiz/components/quiz-status-chip.component';

const BASE: QuizStatus = { triesSinceLastPass: 0, waitDaysAfterNextFailure: 0, mayStartNow: true, requiresConfirmation: false };

/** Quiz status chips (every phase) and the solution list. */
@Component({
  selector: 'fs-gallery-quiz-section',
  imports: [QuizSolutionListComponent, QuizStatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-quiz">Quiz</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.quizStatus">
        <header class="specimen__head"><code>fs-quiz-status-chip</code></header>
        <div style="display: flex; flex-wrap: wrap; gap: 0.5rem">
          @for (status of statuses; track $index) {
            <fs-quiz-status-chip [status]="status" />
          }
        </div>
      </section>
      <section class="specimen" data-testid="gallery.quizSolution">
        <header class="specimen__head"><code>fs-quiz-solution-list</code></header>
        <fs-quiz-solution-list [answers]="solution" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class QuizGallerySection {
  protected readonly statuses: QuizStatus[] = [
    BASE,
    { ...BASE, running: { questionCount: 10, questionsAnswered: 3, timed: false } },
    { ...BASE, requiresConfirmation: true, passedAt: '2026-11-01T10:00:00Z', lastSessionStatus: QuizSessionStatus.Passed },
    { ...BASE, passedAt: '2026-01-06T19:20:00Z', lastSessionStatus: QuizSessionStatus.Passed },
    { ...BASE, passedAt: '2026-01-06T19:20:00Z', expiresInDays: 66, lastSessionStatus: QuizSessionStatus.Passed },
    { ...BASE, lastSessionStatus: QuizSessionStatus.Failed, triesSinceLastPass: 1 },
    { ...BASE, lastSessionStatus: QuizSessionStatus.Failed, currentWaitDays: 30, mayStartNow: false },
    { ...BASE, lastSessionStatus: QuizSessionStatus.Failed, currentWaitDays: -1, mayStartNow: false },
  ];
  protected readonly solution: SolutionLine[] = [
    { text: 'Absagen und Ersatz suchen', rating: AnswerRating.Correct, selected: true, explanation: 'So bleibt die Abholung abgedeckt.' },
    { text: 'Im Team Bescheid geben', rating: AnswerRating.Correct, selected: false },
    { text: 'Einfach nicht hingehen', rating: AnswerRating.Wrong, selected: true, explanation: 'Der Betrieb verlässt sich auf uns.' },
    { text: 'Den Betrieb anrufen', rating: AnswerRating.Neutral, selected: false },
  ];
}

/** Gallery sections of the quiz feature (one file per module, no conflicts). */
export const QUIZ_GALLERY_SECTIONS: readonly Type<unknown>[] = [QuizGallerySection];
