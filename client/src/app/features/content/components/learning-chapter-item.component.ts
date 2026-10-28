import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LearningChapter } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ChapterState } from '../learning-progress';

/** One learning chapter in the overview (C1): number or check mark, title, summary, reading time, read state. */
@Component({
  selector: 'fs-learning-chapter-item',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let c = chapter();
    <a
      class="item"
      [class.item--read]="state() === 'read'"
      [class.item--next]="state() === 'next'"
      [routerLink]="['/learn', c.key]"
      [attr.data-key]="c.key"
      [attr.data-read-state]="state()"
      data-testid="content.learn.chapter"
    >
      <span class="item__badge" aria-hidden="true">
        @if (state() === 'read') {
          <i class="pi pi-check"></i>
        } @else {
          {{ position() }}
        }
      </span>
      <span class="item__text">
        <span class="item__title">{{ c.title }}</span>
        @if (c.summary) {
          <span class="item__summary">{{ c.summary }}</span>
        }
        <span class="item__meta">
          <span
            ><i class="pi pi-clock" aria-hidden="true"></i>
            {{ 'content.learn.minutes' | t: { count: c.estimatedMinutes } }}</span
          >
          @switch (state()) {
            @case ('read') {
              <span class="item__state item__state--read">{{ 'content.learn.read' | t }}</span>
            }
            @case ('next') {
              <span class="item__state item__state--next">{{ 'content.learn.next' | t }}</span>
            }
          }
        </span>
      </span>
      <i class="pi pi-angle-right item__chevron" aria-hidden="true"></i>
    </a>
  `,
  styles: `
    :host {
      display: block;
    }
    .item {
      display: grid;
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: 1rem;
      padding: 1rem 1.1rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-surface);
      color: inherit;
      text-decoration: none;
      transition:
        border-color 120ms ease,
        box-shadow 120ms ease;
    }
    .item:hover,
    .item:focus-visible {
      border-color: var(--fs-primary);
      box-shadow: var(--fs-shadow-sm);
    }
    .item--next {
      border-color: var(--fs-primary);
      box-shadow: inset 4px 0 0 var(--fs-primary);
    }
    .item__badge {
      display: grid;
      place-items: center;
      width: 2.4rem;
      height: 2.4rem;
      border: 2px solid var(--fs-border-strong);
      border-radius: 50%;
      color: var(--fs-text-muted);
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .item--next .item__badge {
      border-color: var(--fs-primary);
      color: var(--fs-primary-strong);
    }
    .item--read .item__badge {
      border-color: var(--fs-primary);
      background: var(--fs-primary);
      color: #fff;
    }
    .item__text {
      display: grid;
      gap: 0.25rem;
      min-width: 0;
    }
    .item__title {
      font-size: 1.05rem;
      font-weight: 700;
    }
    .item__summary {
      display: -webkit-box;
      overflow: hidden;
      -webkit-box-orient: vertical;
      -webkit-line-clamp: 2;
      line-clamp: 2;
      color: var(--fs-text-muted);
      font-size: 0.92rem;
    }
    .item__meta {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.35rem 0.85rem;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .item__meta .pi {
      font-size: 0.78rem;
    }
    .item__state {
      padding: 0.05rem 0.5rem;
      border-radius: 999px;
      font-weight: 650;
    }
    .item__state--read {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .item__state--next {
      box-shadow: inset 0 0 0 1px var(--fs-primary);
      color: var(--fs-primary-strong);
    }
    .item__chevron {
      color: var(--fs-text-muted);
    }
  `,
})
export class LearningChapterItemComponent {
  readonly chapter = input.required<LearningChapter>();
  readonly position = input(1);
  readonly state = input<ChapterState>('open');
}
