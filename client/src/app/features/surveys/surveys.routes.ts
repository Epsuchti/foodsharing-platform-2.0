import { Routes } from '@angular/router';

import { authGuard } from '../../core/auth/auth.guard';

const editor = () => import('./pages/survey-editor-page.component').then((m) => m.SurveyEditorPageComponent);

/**
 * Mounted at '' in app.routes.ts (M5 F6 / SV1): `surveys`, `surveys/new`, `surveys/:surveyId` (editor),
 * `surveys/:surveyId/results` and the bare fill page `survey/:surveyId?storeId=` without the app menu. The server
 * decides access (A36: CAN_MANAGE_SURVEYS to manage, owner/co-author for edit and results, any user to fill).
 * Logged out, authGuard sends the fill page to /login?returnUrl=… including the store query.
 */
export const SURVEYS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [authGuard],
    children: [
      {
        path: 'surveys',
        title: 'surveys.title',
        loadComponent: () => import('./pages/surveys-page.component').then((m) => m.SurveysPageComponent),
      },
      { path: 'surveys/new', title: 'surveys.editor.newTitle', loadComponent: editor },
      {
        path: 'surveys/:surveyId/results',
        title: 'surveys.results.title',
        loadComponent: () => import('./pages/survey-results-page.component').then((m) => m.SurveyResultsPageComponent),
      },
      { path: 'surveys/:surveyId', title: 'surveys.editor.editTitle', loadComponent: editor },
      {
        path: 'survey/:surveyId',
        title: 'surveys.fill.title',
        data: { chrome: 'minimal' },
        loadComponent: () => import('./pages/survey-fill-page.component').then((m) => m.SurveyFillPageComponent),
      },
    ],
  },
];
