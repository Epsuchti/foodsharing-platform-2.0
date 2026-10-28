import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import DOMPurify from 'dompurify';
import { marked } from 'marked';

let linkHook = false;
function ensureLinkHook(): void {
  if (linkHook || typeof window === 'undefined') {
    return;
  }
  linkHook = true;
  // external links open in a new tab without access to this window
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node.tagName === 'A' && /^https?:/.test(node.getAttribute('href') ?? '')) {
      node.setAttribute('target', '_blank');
      node.setAttribute('rel', 'noopener noreferrer');
    }
  });
}

/** Markdown (GFM) → sanitized HTML string (DOMPurify, HTML profile: no scripts, no event handlers). */
export function renderMarkdown(source: string | null | undefined): string {
  ensureLinkHook();
  const html = marked.parse(source ?? '', { async: false, gfm: true, breaks: false });
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
}

/**
 * `<div [innerHTML]="doc.body | markdown"></div>` for CMS/user Markdown. The HTML is sanitized by DOMPurify, so
 * Angular's own sanitizer is bypassed (it would strip harmless attributes such as `target`).
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(source: string | null | undefined): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(renderMarkdown(source));
  }
}
