import { REACTION_KEYS, highlight, quotePost, renderMentions } from './forum.resource';

const ID = '00000000-0000-4000-8000-000000000042';

describe('forum helpers', () => {
  it('quotes a post with a header and drops nested quotes', () => {
    expect(quotePost('Ana', '> old\nHello\nWorld', 'wrote:')).toBe('> **Ana** wrote:\n> Hello\n> World\n\n');
    expect(quotePost('B*b', 'x', 'schrieb:', 'Intro ')).toBe('Intro\n\n> **Bb** schrieb:\n> x\n\n');
  });

  it('renders mention tokens as bold names', () => {
    expect(renderMentions(`Hi @[Ana Muster](user:${ID})!`)).toBe('Hi **@Ana Muster**!');
    expect(renderMentions('@[x](user:nope)')).toBe('@[x](user:nope)');
  });

  it('highlights search terms case-insensitively', () => {
    expect(highlight('Brot und brot', 'brot')).toEqual([
      { text: 'Brot', match: true },
      { text: ' und ', match: false },
      { text: 'brot', match: true },
    ]);
    expect(highlight('a.b', '')).toEqual([{ text: 'a.b', match: false }]);
  });

  it('offers the twelve allowed reactions', () => {
    expect(REACTION_KEYS).toHaveLength(12);
  });
});
