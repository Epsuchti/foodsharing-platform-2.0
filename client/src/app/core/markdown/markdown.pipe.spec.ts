import { renderMarkdown } from './markdown.pipe';

describe('renderMarkdown', () => {
  it('renders GFM', () => {
    const html = renderMarkdown('# Titel\n\n- **eins**\n- zwei');
    expect(html).toContain('<h1>Titel</h1>');
    expect(html).toContain('<strong>eins</strong>');
    expect(html).toContain('<li>zwei</li>');
  });

  it('removes scripts and event handlers', () => {
    const html = renderMarkdown('Hallo <script>alert(1)</script><img src="x" onerror="alert(2)">');
    expect(html).not.toContain('<script');
    expect(html).not.toContain('onerror');
    expect(renderMarkdown('[x](javascript:alert(1))')).not.toContain('javascript:');
  });

  it('opens external links in a new tab without opener', () => {
    const html = renderMarkdown('[foodsharing](https://foodsharing.network)');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer"');
    expect(renderMarkdown('[intern](/learn)')).not.toContain('target=');
  });

  it('styles in-app profile links that start with @ as mentions, nothing else', () => {
    const id = '00000000-0000-4000-8000-000000000042';
    const link = new DOMParser().parseFromString(renderMarkdown(`Hi [@Ana](/profile/${id})`), 'text/html').querySelector('a');
    expect(link?.className).toBe('fs-mention');
    expect(link?.getAttribute('href')).toBe(`/profile/${id}`);
    expect(renderMarkdown(`[Ana](/profile/${id})`)).not.toContain('fs-mention');
    expect(renderMarkdown('[@Ana](https://example.org/profile/x)')).not.toContain('fs-mention');
  });

  it('keeps heading anchors and in-app section links (C1 learning chapters)', () => {
    const html = renderMarkdown('## <a id="b-verhalten-bei-abholungen"></a>B) Verhalten\n\n[B5](/learn/code-of-conduct#b-verhalten-bei-abholungen)');
    expect(html).toContain('<a id="b-verhalten-bei-abholungen"></a>');
    expect(html).toContain('href="/learn/code-of-conduct#b-verhalten-bei-abholungen"');
    expect(html).not.toContain('target="_blank"');
  });

  it('handles empty input', () => {
    expect(renderMarkdown(null)).toBe('');
  });
});
