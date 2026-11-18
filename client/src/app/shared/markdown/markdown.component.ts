import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  afterRenderEffect,
  inject,
  input,
} from '@angular/core';
import { Router } from '@angular/router';

import { SessionService } from '../../core/auth/session.service';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';

/**
 * Renders user or CMS Markdown (legal documents, content pages, learning chapters, …) safely:
 * `<fs-markdown [markdown]="doc.body" />`. In-app links (`/…`) navigate inside the app; mentions (`a.fs-mention`)
 * of the viewer get the `fs-mention--me` ring.
 */
@Component({
  selector: 'fs-markdown',
  imports: [MarkdownPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-markdown', '(click)': 'navigate($event)' },
  template: `<div class="md" [innerHTML]="markdown() | markdown"></div>`,
  // Not encapsulated: the content arrives through [innerHTML], which never carries the emulated `_ngcontent`
  // attribute, so scoped rules (`.md[_ngcontent] h2[_ngcontent]`) matched nothing and every CMS page showed browser
  // defaults (design review A2). Every rule is prefixed with the host class instead.
  encapsulation: ViewEncapsulation.None,
  styles: `
    .fs-markdown {
      display: block;
      min-width: 0;
      line-height: 1.6;
      overflow-wrap: anywhere;
    }
    .fs-markdown .md :is(h1, h2, h3, h4) {
      margin: 1.4em 0 0.5em;
      line-height: 1.25;
      letter-spacing: -0.01em;
    }
    .fs-markdown .md > :first-child {
      margin-top: 0;
    }
    .fs-markdown .md > :last-child {
      margin-bottom: 0;
    }
    .fs-markdown .md h1 {
      font-size: 1.5rem;
    }
    .fs-markdown .md h2 {
      font-size: 1.25rem;
    }
    .fs-markdown .md h3 {
      font-size: 1.08rem;
    }
    .fs-markdown .md h4 {
      font-size: 1rem;
    }
    .fs-markdown .md :is(p, ul, ol, dl, pre) {
      margin: 0 0 0.9em;
    }
    .fs-markdown .md :is(ul, ol) {
      padding-left: 1.25em;
    }
    .fs-markdown .md li + li {
      margin-top: 0.3em;
    }
    .fs-markdown .md li > :is(ul, ol) {
      margin: 0.3em 0 0;
    }
    .fs-markdown .md a {
      color: var(--fs-primary-strong);
      font-weight: 600;
    }
    .fs-markdown .md blockquote {
      margin: 0 0 0.9em;
      padding: 0.25rem 0 0.25rem 0.9rem;
      border-left: 3px solid var(--fs-border-strong);
      color: var(--fs-text-muted);
    }
    .fs-markdown .md blockquote > :last-child {
      margin-bottom: 0;
    }
    .fs-markdown .md code {
      padding: 0.05em 0.35em;
      border-radius: 0.3rem;
      background: var(--fs-surface-muted);
      font-size: 0.9em;
    }
    .fs-markdown .md pre {
      padding: 0.6rem 0.75rem;
      overflow-x: auto;
      border-radius: var(--fs-radius-md, 0.5rem);
      background: var(--fs-surface-muted);
    }
    .fs-markdown .md pre code {
      padding: 0;
      background: none;
    }
    .fs-markdown .md hr {
      margin: 1.25em 0;
      border: 0;
      border-top: 1px solid var(--fs-border);
    }
    .fs-markdown .md img {
      height: auto;
      border-radius: var(--fs-radius-md, 0.5rem);
    }
    /* wide tables scroll inside the text column instead of widening the card on phones */
    .fs-markdown .md table {
      display: block;
      max-width: 100%;
      margin: 0 0 0.9em;
      overflow-x: auto;
      border-collapse: collapse;
    }
    .fs-markdown .md :is(th, td) {
      padding: 0.4rem 0.6rem;
      border: 1px solid var(--fs-border);
      text-align: left;
    }
    @media (max-width: 29.99rem) {
      .fs-markdown .md h1 {
        font-size: 1.3rem;
      }
      .fs-markdown .md h2 {
        font-size: 1.15rem;
      }
    }
  `,
})
export class MarkdownComponent {
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly markdown = input<string | null | undefined>('');

  constructor() {
    afterRenderEffect(() => {
      this.markdown();
      const me = this.session.user()?.id;
      for (const link of Array.from(this.host.nativeElement.querySelectorAll<HTMLAnchorElement>('a.fs-mention'))) {
        link.classList.toggle('fs-mention--me', !!me && link.getAttribute('href') === `/profile/${me}`);
      }
    });
  }

  /** Plain clicks on in-app links use the router instead of reloading the page. */
  protected navigate(event: MouseEvent): void {
    const link = (event.target as Element | null)?.closest?.('a');
    const href = link?.getAttribute('href');
    if (!href || !href.startsWith('/') || href.startsWith('//') || link?.target === '_blank') {
      return;
    }
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }
    event.preventDefault();
    void this.router.navigateByUrl(href);
  }
}
