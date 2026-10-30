import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BlogPostSummary } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { pictureVariant } from '../blog.util';

/** A published post in the blog list (C2): picture, date, region, title, teaser. */
@Component({
  selector: 'fs-blog-post-card',
  imports: [DateTimePipe, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = post();
    <article class="fs-card card" [attr.data-id]="p.id" data-testid="blog.list.post">
      <div class="card__media">
        @if (picture(); as src) {
          <img [src]="src" alt="" loading="lazy" width="320" height="180" />
        } @else {
          <i class="pi pi-book" aria-hidden="true"></i>
        }
      </div>
      <div class="card__body">
        <p class="card__meta">
          @if (p.publishedAt) {
            <time [attr.datetime]="p.publishedAt">{{ p.publishedAt | fsDate: 'date' }}</time>
          }
          @if (p.region) {
            <a
              class="card__region"
              routerLink="/blog"
              [queryParams]="{ region: p.region.id }"
              data-testid="blog.list.region"
              >{{ p.region.name }}</a
            >
          } @else {
            <span class="card__region card__region--global">{{ 'blog.list.global' | t }}</span>
          }
        </p>
        <h2 class="card__title">
          <a [routerLink]="['/blog', p.id]" class="card__link" data-testid="blog.list.open">{{
            p.title
          }}</a>
        </h2>
        @if (p.teaser) {
          <p class="card__teaser">{{ p.teaser }}</p>
        }
        @if (p.author) {
          <p class="card__author">{{ 'blog.list.by' | t: { name: p.author.displayName } }}</p>
        }
      </div>
    </article>
  `,
  styles: `
    :host {
      display: block;
    }
    .card {
      position: relative;
      display: grid;
      grid-template-rows: auto 1fr;
      height: 100%;
      overflow: hidden;
      transition: box-shadow 0.15s ease;
    }
    .card:hover,
    .card:focus-within {
      box-shadow: var(--fs-shadow-md);
    }
    .card__media {
      display: grid;
      place-items: center;
      aspect-ratio: 16 / 9;
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
      font-size: 2rem;
    }
    .card__media img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .card__body {
      display: grid;
      align-content: start;
      gap: 0.45rem;
      padding: 1rem 1.1rem 1.15rem;
    }
    .card__meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.75rem;
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .card__region {
      position: relative;
      z-index: 1;
      padding: 0.05rem 0.5rem;
      border-radius: 999px;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-weight: 600;
      text-decoration: none;
    }
    .card__region--global {
      background: var(--fs-surface-muted);
      color: var(--fs-text-muted);
    }
    .card__title {
      margin: 0;
      font-size: 1.12rem;
      line-height: 1.3;
    }
    .card__link {
      color: inherit;
      text-decoration: none;
    }
    .card__link::after {
      content: '';
      position: absolute;
      inset: 0;
    }
    .card__link:focus-visible {
      outline: none;
    }
    .card:has(.card__link:focus-visible) {
      outline: 2px solid var(--fs-focus-ring, var(--fs-primary));
      outline-offset: 2px;
    }
    .card__teaser {
      display: -webkit-box;
      margin: 0;
      overflow: hidden;
      color: var(--fs-text-muted);
      -webkit-line-clamp: 4;
      -webkit-box-orient: vertical;
    }
    .card__author {
      margin: 0;
      font-size: 0.82rem;
      color: var(--fs-text-muted);
    }
  `,
})
export class BlogPostCardComponent {
  readonly post = input.required<BlogPostSummary>();
  protected readonly picture = computed(() => pictureVariant(this.post().pictureUrl, 'md'));
}
