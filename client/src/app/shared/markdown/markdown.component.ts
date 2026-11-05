import { ChangeDetectionStrategy, Component, ElementRef, afterRenderEffect, inject, input } from '@angular/core';
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
  styles: `
    :host {
      display: block;
      min-width: 0;
      line-height: 1.6;
      overflow-wrap: anywhere;
    }
    .md :is(h1, h2, h3, h4) {
      margin: 1.4em 0 0.5em;
      line-height: 1.25;
      letter-spacing: -0.01em;
    }
    .md :is(h1, h2, h3, h4):first-child {
      margin-top: 0;
    }
    .md h1 {
      font-size: 1.5rem;
    }
    .md h2 {
      font-size: 1.25rem;
    }
    .md h3 {
      font-size: 1.08rem;
    }
    .md p,
    .md ul,
    .md ol {
      margin: 0 0 0.9em;
    }
    .md ul,
    .md ol {
      padding-left: 1.4em;
    }
    .md li + li {
      margin-top: 0.3em;
    }
    .md a {
      color: var(--fs-primary-strong);
      font-weight: 600;
    }
    .md blockquote {
      margin: 0 0 0.9em;
      padding: 0.25rem 0 0.25rem 0.9rem;
      border-left: 3px solid var(--fs-border-strong);
      color: var(--fs-text-muted);
    }
    .md code {
      padding: 0.05em 0.35em;
      border-radius: 0.3rem;
      background: var(--fs-surface-muted);
      font-size: 0.9em;
    }
    .md table {
      width: 100%;
      margin: 0 0 0.9em;
      border-collapse: collapse;
    }
    .md :is(th, td) {
      padding: 0.4rem 0.6rem;
      border: 1px solid var(--fs-border);
      text-align: left;
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
