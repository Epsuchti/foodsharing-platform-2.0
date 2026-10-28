import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { QuizOverview, QuizzesService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { QUIZ_ICONS, quizSlug } from '../quiz-kind';

/** /quizzes/edit (Q3): the quizzes the user may edit or read (quiz working-group admins per `quiz_kinds`, ORGA). */
@Component({
  selector: 'fs-quiz-editor-list-page',
  imports: [...DATA_STATE_IMPORTS, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'quiz.editor.listTitle' | t" [subtitle]="'quiz.editor.listSubtitle' | t" icon="pi pi-pencil" />
    <fs-data-state
      [state]="quizzes"
      skeleton="list"
      emptyIcon="pi pi-lock"
      [emptyTitle]="'quiz.editor.noQuizzes' | t"
      [emptyText]="'quiz.editor.noQuizzesText' | t"
      data-testid="quiz.editor.list"
    >
      <ng-template [fsData]="quizzes">
        <ul class="list fs-card">
          @for (quiz of editable(); track quiz.kind) {
            <li>
              <a class="item" [routerLink]="['/quizzes/edit', slug(quiz)]" [attr.data-kind]="quiz.kind" data-testid="quiz.editor.listItem">
                <i [class]="icons[quiz.kind]" aria-hidden="true"></i>
                <span class="item__name">{{ quiz.name }}</span>
                <span class="item__meta">{{ (quiz.mayEdit ? 'quiz.editor.canEdit' : 'quiz.editor.canRead') | t }}</span>
                <i class="pi pi-angle-right" aria-hidden="true"></i>
              </a>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../quiz.scss',
  styles: `
    .list {
      display: grid;
      gap: 0.2rem;
      margin: 0;
      padding: 0.5rem;
      list-style: none;
    }
    .item {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 0.85rem;
      padding: 0.75rem 0.85rem;
      border-radius: var(--fs-radius-md);
      color: inherit;
      text-decoration: none;
    }
    .item:hover,
    .item:focus-visible {
      background: var(--fs-primary-soft);
    }
    .item > .pi:first-child {
      color: var(--fs-primary-strong);
    }
    .item__name {
      font-weight: 650;
    }
    .item__meta {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
  `,
})
export class QuizEditorListPageComponent {
  private readonly api = inject(QuizzesService);
  private readonly i18n = inject(I18nService);
  protected readonly icons = QUIZ_ICONS;

  protected readonly quizzes = loadResource<QuizOverview[], string>({
    params: () => this.i18n.language(),
    loader: () => this.api.listQuizzes(),
    isEmpty: (list) => !list.some((quiz) => quiz.mayEdit || quiz.mayRead),
  });

  protected readonly editable = computed(() =>
    (this.quizzes.data() ?? []).filter((quiz) => quiz.mayEdit || quiz.mayRead),
  );

  protected slug(quiz: QuizOverview): string {
    return quizSlug(quiz.kind);
  }
}
