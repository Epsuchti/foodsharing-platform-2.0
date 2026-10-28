import { formatSize, parseAddresses, sanitizeMailHtml, textToHtml } from './mailboxes.resource';

describe('mailbox helpers', () => {
  it('sanitises mail HTML: scripts, handlers and remote images go, links open in a new tab', () => {
    const html = sanitizeMailHtml(
      '<p onclick="x()">Hi<script>alert(1)</script></p><img src="https://tracker.example/p.gif"><img src="data:image/png;base64,AA=="><a href="https://foodsharing.network">fs</a>',
    );
    expect(html).not.toContain('script');
    expect(html).not.toContain('onclick');
    expect(html).not.toContain('tracker.example');
    expect(html).toContain('data:image/png');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('rel="noopener noreferrer nofollow"');
  });

  it('turns plain text into escaped paragraphs', () => {
    expect(textToHtml('Hallo <b>\nZeile\n\nNeu')).toBe('<p>Hallo &lt;b&gt;<br>Zeile</p><p>Neu</p>');
  });

  it('parses address lists', () => {
    expect(parseAddresses('a@b.ch, falsch; c@d.org')).toEqual({ valid: ['a@b.ch', 'c@d.org'], invalid: ['falsch'] });
  });

  it('formats sizes', () => {
    expect([formatSize(500), formatSize(2048), formatSize(3 * 1024 * 1024)]).toEqual(['500 B', '2 kB', '3.0 MB']);
  });
});
