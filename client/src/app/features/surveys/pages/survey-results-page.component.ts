import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { SurveyResults, SurveysService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { cellText, resultsCsv } from '../surveys.resource';

/** /surveys/:surveyId/results (SV1, owner and co-authors): table questions × responses and a client-side CSV export. */
@Component({
  selector: 'fs-survey-results-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'surveys.results.title' | t" icon="pi pi-table" [backLink]="['/surveys', surveyId()]" [backLabel]="'surveys.editor.editTitle' | t" />
    <fs-data-state [state]="results" skeleton="table" [emptyTitle]="'surveys.results.emptyTitle' | t" [emptyText]="'surveys.results.emptyText' | t" emptyIcon="pi pi-table" data-testid="surveys.results.state">
      <ng-template [fsData]="results" let-r>
        <div class="bar">
          <span data-testid="surveys.results.count">{{ 'surveys.responseCount' | t: { count: r.responseCount } }}</span>
          <p-button icon="pi pi-download" [outlined]="true" [label]="'surveys.results.csv' | t" (onClick)="exportCsv(r)" data-testid="surveys.results.csv" />
        </div>
        <div class="scroll fs-card" tabindex="0" role="region" [attr.aria-label]="'surveys.results.title' | t">
          <table data-testid="surveys.results.table">
            <thead>
              <tr>
                <th scope="col">{{ 'surveys.results.user' | t }}</th>
                <th scope="col">{{ 'surveys.results.submittedAt' | t }}</th>
                @for (q of r.questions; track q.id) {<th scope="col">{{ q.label }}</th>}
              </tr>
            </thead>
            <tbody>
              @for (row of r.rows; track $index; let i = $index) {
                <tr data-testid="surveys.results.row">
                  <td>{{ row.user.displayName }}</td>
                  <td>{{ row.submittedAt | fsDate: 'dateTime' }}</td>
                  @for (q of r.questions; track q.id) {<td>{{ cell(r, i, q) }}</td>}
                </tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 72rem); margin: 0 auto; }
    .bar { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap; }
    .scroll { overflow-x: auto; }
    table { border-collapse: collapse; width: 100%; font-size: 0.9rem; }
    th, td { padding: 0.5rem 0.75rem; text-align: left; vertical-align: top; border-bottom: 1px solid var(--fs-border); }
    th { background: var(--fs-surface-muted); font-weight: 600; white-space: nowrap; }
  `,
})
export class SurveyResultsPageComponent {
  private readonly api = inject(SurveysService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  readonly surveyId = input.required<string>();

  protected readonly results = loadResource<SurveyResults, string>({
    params: () => this.surveyId(),
    loader: (surveyId) => this.api.getSurveyResults({ surveyId }),
    isEmpty: (r) => r.rows.length === 0,
  });

  private labels() {
    return { yes: this.i18n.t('surveys.yes'), no: this.i18n.t('surveys.no') };
  }

  protected cell(r: SurveyResults, i: number, q: SurveyResults['questions'][number]): string {
    return cellText(r, i, q, this.labels());
  }

  protected exportCsv(r: SurveyResults): void {
    const csv = resultsCsv(r, { user: this.i18n.t('surveys.results.user'), submittedAt: this.i18n.t('surveys.results.submittedAt') }, this.labels());
    const view = this.document.defaultView;
    if (!view?.URL?.createObjectURL) return;
    const url = view.URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
    const link = this.document.createElement('a');
    link.href = url;
    link.download = `survey-${this.surveyId()}.csv`;
    link.style.display = 'none';
    this.document.body.appendChild(link);
    link.click();
    link.remove();
    view.setTimeout(() => view.URL.revokeObjectURL(url), 1000);
  }
}
