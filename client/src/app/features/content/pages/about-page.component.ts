import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TPipe } from '../../../core/i18n/t.pipe';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ABOUT_FEATURE_PAGES, ABOUT_PAGES, AboutPage } from '../content-paths';

/** /about (C1, public): overview of the about pages (CMS pages and the team, statistics, FAQ, release notes pages). */
@Component({
  selector: 'fs-about-page',
  imports: [PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.about.title' | t"
      [subtitle]="'content.about.subtitle' | t"
      icon="pi pi-info-circle"
    />
    @for (section of sections; track section.key) {
      <section class="section" [attr.aria-labelledby]="'about-' + section.key">
        <h2 class="section__title" [id]="'about-' + section.key">
          {{ 'content.about.section.' + section.key | t }}
        </h2>
        <ul class="grid">
          @for (page of section.pages; track page.id) {
            <li>
              <a
                class="fs-card tile"
                [routerLink]="page.path"
                [attr.data-page]="page.id"
                data-testid="content.about.link"
              >
                <i [class]="page.icon + ' tile__icon'" aria-hidden="true"></i>
                <span class="tile__text">
                  <span class="tile__title">{{ 'content.about.page.' + page.id | t }}</span>
                  <span class="tile__teaser">{{ 'content.about.text.' + page.id | t }}</span>
                </span>
              </a>
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .section + .section {
      margin-top: 2rem;
    }
    .section__title {
      margin: 0 0 0.75rem;
      font-size: 1.05rem;
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 16rem), 1fr));
      gap: 0.85rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .tile {
      display: flex;
      align-items: flex-start;
      gap: 0.85rem;
      height: 100%;
      padding: 1rem 1.1rem;
      color: inherit;
      text-decoration: none;
      transition:
        border-color 150ms ease,
        box-shadow 150ms ease;
    }
    .tile:hover,
    .tile:focus-visible {
      border-color: var(--fs-primary);
    }
    .tile__icon {
      display: grid;
      flex: none;
      place-items: center;
      width: 2.4rem;
      height: 2.4rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.05rem;
    }
    .tile__text {
      display: grid;
      gap: 0.2rem;
    }
    .tile__title {
      font-weight: 700;
    }
    .tile__teaser {
      color: var(--fs-text-muted);
      font-size: 0.88rem;
    }
  `,
})
export class AboutPageComponent {
  protected readonly sections: readonly { key: string; pages: readonly AboutPage[] }[] = [
    { key: 'pages', pages: ABOUT_PAGES },
    { key: 'more', pages: ABOUT_FEATURE_PAGES },
  ];
}
