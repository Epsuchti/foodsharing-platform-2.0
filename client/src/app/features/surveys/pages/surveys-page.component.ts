import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';

import { StoreListItem, StoresService, SurveySummary, SurveysService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { surveyLink } from '../surveys.resource';

/**
 * /surveys (SV1): own and co-authored surveys with response and question counts, links to editor and results, and
 * "copy link" (optionally bound to one of my stores: STORE questions are answered only through `?storeId=`).
 */
@Component({
  selector: 'fs-surveys-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, PageHeaderComponent, RouterLink, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'surveys.title' | t" icon="pi pi-list-check">
      <p-button [label]="'surveys.new' | t" icon="pi pi-plus" routerLink="/surveys/new" data-testid="surveys.list.new" />
    </fs-page-header>
    <fs-data-state [state]="surveys" skeleton="list" [emptyTitle]="'surveys.emptyTitle' | t" [emptyText]="'surveys.emptyText' | t" emptyIcon="pi pi-list-check" data-testid="surveys.list.state">
      <ng-template [fsData]="surveys" let-list>
        <div class="link-store fs-card">
          <label for="survey-link-store">{{ 'surveys.linkStore' | t }}</label>
          <p-select inputId="survey-link-store" [options]="storeOptions()" optionLabel="name" optionValue="id" [showClear]="true" [filter]="true" [placeholder]="'surveys.linkNoStore' | t" [ngModel]="linkStore()" (ngModelChange)="linkStore.set($event)" appendTo="body" data-testid="surveys.list.linkStore" />
          <small class="muted">{{ 'surveys.linkStoreHint' | t }}</small>
        </div>
        <ul class="items fs-card">
          @for (s of list; track s.id) {
            <li class="item" data-testid="surveys.list.item" [attr.data-survey-id]="s.id">
              <span class="item__text">
                <a class="item__name" [routerLink]="['/surveys', s.id]" data-testid="surveys.list.edit">{{ s.title }}</a>
                <span class="muted">
                  {{ 'surveys.byOwner' | t: { name: s.owner.displayName } }} · {{ s.createdAt | fsDate: 'date' }} ·
                  {{ 'surveys.questionCount' | t: { count: s.questionCount } }}
                </span>
              </span>
              <span class="item__side">
                <a class="responses" [routerLink]="['/surveys', s.id, 'results']" data-testid="surveys.list.results">{{ 'surveys.responseCount' | t: { count: s.responseCount } }}</a>
                <p-button icon="pi pi-link" size="small" [outlined]="true" [label]="'surveys.copyLink' | t" [ariaLabel]="('surveys.copyLink' | t) + ': ' + s.title" (onClick)="copy(s)" data-testid="surveys.list.copyLink" />
              </span>
            </li>
          }
        </ul>
        @if (lastLink(); as link) {
          <p class="muted last-link" data-testid="surveys.list.lastLink">{{ link }}</p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }
    .link-store { display: grid; gap: 0.3rem; padding: 0.75rem 1rem; }
    .link-store label { font-weight: 600; }
    .items { list-style: none; margin: 0; padding: 0; }
    .item { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem; }
    .item + .item { border-top: 1px solid var(--fs-border); }
    .item__text { display: grid; gap: 0.15rem; flex: 1 1 16rem; min-width: 0; }
    .item__name { font-weight: 600; color: inherit; }
    .item__side { display: flex; align-items: center; gap: 0.75rem; }
    .muted { color: var(--fs-text-muted); font-size: 0.875rem; }
    .responses { font-weight: 600; color: var(--fs-primary-strong); }
    .last-link { margin: 0; word-break: break-all; }
  `,
})
export class SurveysPageComponent {
  private readonly api = inject(SurveysService);
  private readonly storesApi = inject(StoresService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  protected readonly linkStore = signal<string | null>(null);
  protected readonly lastLink = signal<string | null>(null);

  protected readonly surveys = loadResource<SurveySummary[]>({
    loader: () => this.api.listSurveys(),
  });

  private readonly stores = loadResource<StoreListItem[]>({
    loader: () => this.storesApi.listMyStores({ excludeInactive: true }),
  });
  protected readonly storeOptions = computed(() => this.stores.data() ?? []);

  protected copy(s: SurveySummary): void {
    const link = surveyLink(this.document.location.origin, s.id, this.linkStore());
    this.lastLink.set(link);
    void navigator.clipboard?.writeText(link).then(
      () => this.messages.add({ severity: 'success', summary: this.i18n.t('surveys.linkCopied') }),
      () => undefined,
    );
  }
}
