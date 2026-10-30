import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { InputText } from '@openng/optimus-ui/inputtext';

import { FaqEntry, Language, PublicContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { filterFaq } from '../content.util';

/** /faq (C1, public): published FAQ entries in the UI language as an accordion, filtered by a search field. */
@Component({
  selector: 'fs-faq-page',
  imports: [...DATA_STATE_IMPORTS, InputText, MarkdownComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.faq.title' | t"
      [subtitle]="'content.faq.subtitle' | t"
      icon="pi pi-comments"
    />
    <fs-data-state
      [state]="faq"
      skeleton="list"
      emptyIcon="pi pi-comments"
      [emptyTitle]="'content.faq.empty' | t"
      data-testid="content.faq.state"
    >
      <ng-template [fsData]="faq" let-entries>
        <div class="search">
          <label class="fs-visually-hidden" for="faq-filter">{{ 'content.faq.filter' | t }}</label>
          <i class="pi pi-search" aria-hidden="true"></i>
          <input
            pInputText
            id="faq-filter"
            type="search"
            [value]="query()"
            (input)="query.set($any($event.target).value)"
            [placeholder]="'content.faq.filter' | t"
            data-testid="content.faq.filter"
          />
        </div>
        @let shown = visible(entries);
        <p class="count" aria-live="polite" data-testid="content.faq.count">
          {{ 'content.faq.count' | t: { count: shown.length } }}
        </p>
        @if (shown.length) {
          <div class="fs-card list">
            @for (entry of shown; track entry.slug) {
              <details class="item" [attr.data-slug]="entry.slug" data-testid="content.faq.entry">
                <summary data-testid="content.faq.question">
                  <span>{{ entry.question }}</span>
                  <i class="pi pi-chevron-down item__chevron" aria-hidden="true"></i>
                </summary>
                <fs-markdown
                  class="item__answer"
                  [markdown]="entry.answer"
                  data-testid="content.faq.answer"
                />
              </details>
            }
          </div>
        } @else {
          <p class="fs-card none" data-testid="content.faq.noMatch">
            <i class="pi pi-filter-slash" aria-hidden="true"></i>{{ 'content.faq.noMatch' | t }}
          </p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 52rem);
      margin: 0 auto;
    }
    .search {
      position: relative;
      margin-bottom: 0.5rem;
    }
    .search .pi {
      position: absolute;
      top: 50%;
      left: 0.85rem;
      color: var(--fs-text-muted);
      transform: translateY(-50%);
    }
    .search input {
      width: 100%;
      padding-left: 2.4rem;
    }
    .count {
      margin: 0 0 0.75rem;
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .list {
      display: grid;
      padding: 0.35rem 1rem;
    }
    .item + .item {
      border-top: 1px solid var(--fs-border);
    }
    .item summary {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      min-height: 2.75rem;
      padding: 0.75rem 0;
      font-weight: 650;
      list-style: none;
      cursor: pointer;
    }
    .item summary::-webkit-details-marker {
      display: none;
    }
    .item__chevron {
      flex: none;
      color: var(--fs-text-muted);
      transition: transform 150ms ease;
    }
    .item[open] .item__chevron {
      transform: rotate(180deg);
    }
    .item__answer {
      display: block;
      padding: 0 0 1rem;
    }
    .none {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      padding: 1rem 1.1rem;
      color: var(--fs-text-muted);
    }
    @media (prefers-reduced-motion: reduce) {
      .item__chevron {
        transition: none;
      }
    }
  `,
})
export class FaqPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly i18n = inject(I18nService);

  protected readonly query = signal('');

  protected readonly faq = loadResource<FaqEntry[], string>({
    params: () => this.i18n.language(),
    loader: (lang) => this.api.listFaq({ lang: lang as Language }),
  });

  protected visible(entries: FaqEntry[]): FaqEntry[] {
    return filterFaq(entries, this.query());
  }
}
