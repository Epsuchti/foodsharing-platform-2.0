import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { ContentPageKind, ContentPageSummary, ContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { LANGUAGES } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

const KIND_ORDER: readonly ContentPageKind[] = [
  ContentPageKind.LearningChapter,
  ContentPageKind.Page,
  ContentPageKind.Faq,
];

/**
 * /admin/content (C1, ORGA and PR working-group admins): all content pages incl. drafts, grouped by kind, with the
 * languages they exist in; opens the editor. Links to the legal document versions.
 */
@Component({
  selector: 'fs-content-admin-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.admin.title' | t"
      [subtitle]="'content.admin.subtitle' | t"
      icon="pi pi-book"
    >
      <p-button
        [label]="'content.admin.legal' | t"
        icon="pi pi-file-edit"
        severity="secondary"
        [outlined]="true"
        routerLink="/admin/legal"
        data-testid="content.admin.legal"
      />
      <p-button
        [label]="'content.admin.new' | t"
        icon="pi pi-plus"
        routerLink="/admin/content/new"
        data-testid="content.admin.new"
      />
    </fs-page-header>

    <fs-data-state
      [state]="pages"
      skeleton="list"
      emptyIcon="pi pi-book"
      [emptyTitle]="'content.admin.empty' | t"
      [askWho]="'content.admin.askWho' | t"
      data-testid="content.admin.state"
    >
      <ng-template [fsData]="pages">
        @for (group of groups(); track group.kind) {
          <section class="group" [attr.data-kind]="group.kind">
            <h2 class="group__title">
              {{ 'content.kind.' + group.kind | t }} <span>{{ group.pages.length }}</span>
            </h2>
            <ul class="fs-card list">
              @for (page of group.pages; track page.slug) {
                <li>
                  <a
                    class="row"
                    [routerLink]="['/admin/content', page.slug]"
                    [attr.data-slug]="page.slug"
                    data-testid="content.admin.page"
                  >
                    <span class="row__main">
                      <span class="row__title">{{ title(page) }}</span>
                      <code class="row__slug">{{ page.slug }}</code>
                    </span>
                    <span class="row__langs" [attr.aria-label]="'content.admin.languages' | t">
                      @for (lang of languages; track lang) {
                        <span class="lang" [class.lang--missing]="!has(page, lang)">{{
                          lang
                        }}</span>
                      }
                    </span>
                    <span class="row__status">
                      @if (page.published) {
                        <span class="chip chip--published">{{
                          'content.admin.published' | t
                        }}</span>
                      } @else {
                        <span class="chip chip--draft">{{ 'content.admin.draft' | t }}</span>
                      }
                      @if (page.updatedAt) {
                        <span class="row__date">{{ page.updatedAt | fsDate: 'date' }}</span>
                      }
                    </span>
                    <i class="pi pi-angle-right row__chevron" aria-hidden="true"></i>
                  </a>
                </li>
              }
            </ul>
          </section>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
    }
    .group + .group {
      margin-top: 1.5rem;
    }
    .group__title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0 0 0.6rem;
      font-size: 1rem;
    }
    .group__title span {
      padding: 0 0.5rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.8rem;
    }
    .list {
      display: grid;
      margin: 0;
      padding: 0.35rem;
      list-style: none;
    }
    .row {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto auto;
      align-items: center;
      gap: 0.5rem 1rem;
      padding: 0.7rem 0.8rem;
      border-radius: var(--fs-radius-md);
      color: inherit;
      text-decoration: none;
    }
    .row:hover,
    .row:focus-visible {
      background: var(--fs-primary-soft);
    }
    .row__main {
      display: grid;
      gap: 0.1rem;
      min-width: 0;
    }
    .row__title {
      font-weight: 650;
    }
    .row__slug {
      color: var(--fs-text-muted);
      font-size: 0.78rem;
    }
    .row__langs {
      display: flex;
      gap: 0.2rem;
    }
    .lang {
      min-width: 2rem;
      padding: 0.05rem 0.3rem;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 0.7rem;
      font-weight: 700;
      text-align: center;
      text-transform: uppercase;
    }
    .lang--missing {
      background: transparent;
      color: var(--fs-text-muted);
      outline: 1px dashed var(--fs-border-strong);
      text-decoration: line-through;
    }
    .row__status {
      display: grid;
      justify-items: end;
      gap: 0.15rem;
    }
    .row__date {
      color: var(--fs-text-muted);
      font-size: 0.75rem;
    }
    .chip {
      padding: 0.05rem 0.55rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 650;
    }
    .chip--published {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .chip--draft {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .row__chevron {
      display: none;
    }
    @media (max-width: 40rem) {
      .row {
        grid-template-columns: minmax(0, 1fr) auto;
      }
      .row__langs {
        grid-column: 1;
        grid-row: 2;
      }
      .row__status {
        grid-row: 1 / span 2;
        grid-column: 2;
      }
    }
  `,
})
export class ContentAdminPageComponent {
  private readonly api = inject(ContentService);
  private readonly i18n = inject(I18nService);

  protected readonly languages = LANGUAGES;

  protected readonly pages = loadResource<ContentPageSummary[]>({
    loader: () => this.api.listContentPages({}),
  });

  protected readonly groups = computed(() => {
    const pages = this.pages.data() ?? [];
    return KIND_ORDER.map((kind) => ({
      kind,
      pages: pages.filter((page) => page.kind === kind).sort((a, b) => a.sortOrder - b.sortOrder),
    })).filter((group) => group.pages.length > 0);
  });

  protected title(page: ContentPageSummary): string {
    const titles = page.titles ?? {};
    return titles[this.i18n.language()] || titles['de'] || Object.values(titles)[0] || page.slug;
  }

  protected has(page: ContentPageSummary, lang: string): boolean {
    return page.languages ? page.languages.includes(lang as never) : !!page.titles?.[lang];
  }
}
