import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { ContentPage, Language, PublicContentService, PublicPartnerStore } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/** /pages/:slug (C1, public): a published CMS page in the UI language (falls back to German, then any language). */
@Component({
  selector: 'fs-content-page',
  imports: [...DATA_STATE_IMPORTS, DateTimePipe, MarkdownComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="page" skeleton="detail" backLink="/" data-testid="content.page.state">
      <ng-template [fsData]="page" let-p>
        <fs-page-header [title]="p.title" />
        <article class="fs-card page" [attr.data-slug]="slug()" data-testid="content.page.article">
          @if (p.fallback) {
            <p class="page__fallback" data-testid="content.page.fallback">
              <i class="pi pi-language" aria-hidden="true"></i>{{ 'content.page.fallback' | t }}
            </p>
          }
          <fs-markdown [markdown]="p.body" data-testid="content.page.body" />
          @if (p.updatedAt) {
            <p class="page__updated">
              {{ 'content.page.updated' | t: { date: (p.updatedAt | fsDate: 'date') } }}
            </p>
          }
        </article>
      </ng-template>
    </fs-data-state>
    @if (slug() === 'partners') {
      <section class="fs-card page" data-testid="content.partners">
        <h2 class="partners__title">{{ 'content.partners.title' | t }}</h2>
        <fs-data-state [state]="partners" skeleton="list" [compact]="true" [emptyTitle]="'content.partners.empty' | t" data-testid="content.partners.state">
          <ng-template [fsData]="partners" let-stores>
            <ul class="partners">
              @for (store of stores; track store.name + store.city) {
                <li data-testid="content.partners.store">
                  <strong>{{ store.name }}</strong>
                  @if (store.city) {
                    <span class="muted"> · {{ store.city }}</span>
                  }
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 48rem);
      margin: 0 auto;
    }
    .page {
      display: grid;
      gap: 1rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2.25rem);
    }
    .fs-card + .fs-card {
      margin-top: 1rem;
    }
    .partners__title {
      margin: 0;
      font-size: 1.1rem;
    }
    .partners {
      display: grid;
      gap: 0.4rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .page__fallback {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
    .page__updated {
      margin: 0;
      padding-top: 0.75rem;
      border-top: 1px solid var(--fs-border);
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
  `,
})
export class ContentPageComponent {
  private readonly api = inject(PublicContentService);
  private readonly i18n = inject(I18nService);

  /** Route parameter. */
  readonly slug = input.required<string>();

  protected readonly page = loadResource<ContentPage, { slug: string; lang: string }>({
    params: () => ({ slug: this.slug(), lang: this.i18n.language() }),
    loader: ({ slug, lang }) => this.api.getContentPage({ slug, lang: lang as Language }),
  });

  /** Stores flagged as publicly named (decision 153, S17 D8); only the partners page asks for them. */
  protected readonly partners = loadResource<PublicPartnerStore[], true>({
    params: () => (this.slug() === 'partners' ? true : undefined),
    loader: () => this.api.listPublicPartnerStores(),
  });
}
