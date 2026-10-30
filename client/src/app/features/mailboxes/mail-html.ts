import DOMPurify from 'dompurify';

// Own file: the nav badges import mailboxes.resource after the first page, and DOMPurify belongs only to the
// mail view (D11.5.3: nothing heavy right after the first page).

/**
 * Mail HTML for the sandboxed iframe: DOMPurify on top of the server-side jsoup pass (A35, risk 5); remote images
 * are dropped (tracking pixels), links open in a new tab.
 */
export function sanitizeMailHtml(html: string): string {
  const clean = DOMPurify.sanitize(html, {
    FORBID_TAGS: ['style', 'script', 'iframe', 'object', 'embed', 'form', 'input', 'link', 'meta', 'base'],
    FORBID_ATTR: ['srcset'],
  });
  const doc = new DOMParser().parseFromString(clean, 'text/html');
  doc.querySelectorAll('img').forEach((img) => {
    const src = img.getAttribute('src') ?? '';
    if (!src.startsWith('data:') && !src.startsWith('cid:')) {
      img.remove();
    }
  });
  doc.querySelectorAll('a[href]').forEach((a) => {
    a.setAttribute('target', '_blank');
    a.setAttribute('rel', 'noopener noreferrer nofollow');
  });
  const style =
    '<style>body{font:15px/1.5 system-ui,sans-serif;margin:0;padding:0.5rem;color:#1f2933;background:#fff;overflow-wrap:anywhere}img{max-width:100%}</style>';
  return `<!doctype html><html><head><meta charset="utf-8">${style}</head><body>${doc.body.innerHTML}</body></html>`;
}
