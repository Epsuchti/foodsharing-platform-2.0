import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

/** Markdown → sanitized HTML (DOMPurify; links open in a new tab with rel=noopener). */
export function renderMarkdown(source: string | null | undefined): string {
  const html = marked.parse(source ?? '', { async: false, gfm: true, breaks: false });
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}

let hooked = false;
function ensureLinkHook(): void {
  if (hooked || typeof window === 'undefined') {
    return;
  }
  hooked = true;
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && node.getAttribute('href')?.match(/^https?:/)) {
      node.setAttribute('target', '_blank');
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
}

/**
 * Renders user or CMS Markdown (legal documents, content pages, learning chapters, …) safely:
 * `<fs-markdown [markdown]="doc.body" />`.
 */
@Component({
  selector: 'fs-markdown',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-markdown' },
  template: `<div class="md" [innerHTML]="html()"></div>`,
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
  private readonly sanitizer = inject(DomSanitizer);
  readonly markdown = input<string | null | undefined>('');

  protected readonly html = computed<SafeHtml>(() => {
    ensureLinkHook();
    // DOMPurify already sanitized the HTML; Angular's own sanitizer would strip harmless attributes again.
    return this.sanitizer.bypassSecurityTrustHtml(renderMarkdown(this.markdown()));
  });
}
