import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AnswerRating } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';

export interface SolutionLine {
  id?: string;
  text: string;
  rating: AnswerRating;
  selected: boolean;
  explanation?: string;
}

/** The answers of a question with their rating, the user's choice and the explanation (session solution, results). */
@Component({
  selector: 'fs-quiz-solution-list',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="answers">
      @for (answer of answers(); track $index) {
        <li
          [class]="'solution solution--' + answer.rating"
          [class.solution--missed]="answer.rating === 'CORRECT' && !answer.selected"
          [class.solution--chosen-wrong]="answer.rating === 'WRONG' && answer.selected"
          [attr.data-rating]="answer.rating"
          [attr.data-selected]="answer.selected"
          [attr.data-testid]="testId()"
        >
          <i [class]="icon(answer.rating)" aria-hidden="true"></i>
          <span class="solution__text">{{ answer.text }}</span>
          <span class="solution__meta">
            <span>{{ 'quiz.rating.' + answer.rating | t }}</span>
            @if (answer.selected) {
              <span>· {{ 'quiz.solution.yourChoice' | t }}</span>
            } @else if (answer.rating === 'CORRECT') {
              <span>· {{ 'quiz.solution.missed' | t }}</span>
            }
          </span>
          @if (answer.explanation) {
            <p class="solution__explanation">{{ answer.explanation }}</p>
          }
        </li>
      }
    </ul>
  `,
  styleUrl: './quiz-solution-list.component.scss',
})
export class QuizSolutionListComponent {
  readonly answers = input.required<readonly SolutionLine[]>();
  readonly testId = input('quiz.solution.answer');

  protected icon(rating: AnswerRating): string {
    return rating === 'CORRECT' ? 'pi pi-check-circle' : rating === 'WRONG' ? 'pi pi-times-circle' : 'pi pi-circle';
  }
}
