import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';
import { quizKindAt } from './quiz-kind';

/**
 * Mounted at 'quizzes' in app.routes.ts; paths inside are relative to that mount.
 * Quizzes (Q1), sessions and results (Q2), quiz session history (A8) and the quiz editor (Q3). Access per quiz is
 * decided by the server (`mayTry` / `mayRead` / `mayEdit`, 403 → forbidden state).
 */
export const QUIZ_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    title: 'quiz.list.pageTitle',
    loadComponent: () => import('./pages/quizzes-page.component').then((m) => m.QuizzesPageComponent),
  },
  {
    path: 'history',
    canActivate: [authGuard],
    title: 'quiz.history.pageTitle',
    loadComponent: () => import('./pages/quiz-history-page.component').then((m) => m.QuizHistoryPageComponent),
  },
  {
    path: 'history/:userId',
    canActivate: [authGuard],
    title: 'quiz.history.userPageTitle',
    loadComponent: () => import('./pages/quiz-history-page.component').then((m) => m.QuizHistoryPageComponent),
  },
  {
    path: 'edit',
    canActivate: [authGuard],
    title: 'quiz.editor.listTitle',
    loadComponent: () =>
      import('./pages/quiz-editor-list-page.component').then((m) => m.QuizEditorListPageComponent),
  },
  {
    path: 'edit/:kind',
    canMatch: [quizKindAt(1)],
    canActivate: [authGuard],
    title: 'quiz.editor.pageTitle',
    loadComponent: () => import('./pages/quiz-editor-page.component').then((m) => m.QuizEditorPageComponent),
  },
  {
    path: ':kind',
    canMatch: [quizKindAt(0)],
    canActivate: [authGuard],
    title: 'quiz.intro.pageTitle',
    loadComponent: () => import('./pages/quiz-intro-page.component').then((m) => m.QuizIntroPageComponent),
  },
  {
    path: ':kind/session',
    canMatch: [quizKindAt(0)],
    canActivate: [authGuard],
    title: 'quiz.session.pageTitle',
    loadComponent: () => import('./pages/quiz-session-page.component').then((m) => m.QuizSessionPageComponent),
  },
  {
    path: ':kind/results',
    canMatch: [quizKindAt(0)],
    canActivate: [authGuard],
    title: 'quiz.results.pageTitle',
    loadComponent: () => import('./pages/quiz-results-page.component').then((m) => m.QuizResultsPageComponent),
  },
];
