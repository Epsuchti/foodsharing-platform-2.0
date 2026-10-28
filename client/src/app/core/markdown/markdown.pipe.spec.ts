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

  it('handles empty input', () => {
    expect(renderMarkdown(null)).toBe('');
  });
});
