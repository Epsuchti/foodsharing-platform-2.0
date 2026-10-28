import { bodySegments, insertMention, mentionQuery, mentionToken } from './message-body';

const ID = '00000000-0000-4000-8000-000000000042';

describe('message body', () => {
  it('splits mentions and links from text', () => {
    expect(bodySegments(`Hi @[Ana Muster](user:${ID}), see https://example.org/x. Bye`)).toEqual([
      { kind: 'text', text: 'Hi ' },
      { kind: 'mention', name: 'Ana Muster', userId: ID },
      { kind: 'text', text: ', see ' },
      { kind: 'link', href: 'https://example.org/x' },
      { kind: 'text', text: '. Bye' },
    ]);
    expect(bodySegments('plain')).toEqual([{ kind: 'text', text: 'plain' }]);
    expect(bodySegments('javascript:alert(1)')).toEqual([{ kind: 'text', text: 'javascript:alert(1)' }]);
  });

  it('builds and inserts mention tokens', () => {
    expect(mentionToken('A [b]', ID)).toBe(`@[A b](user:${ID})`);
    expect(mentionQuery('hello @an')).toBe('an');
    expect(mentionQuery('hello @')).toBe('');
    expect(mentionQuery('mail@an')).toBeNull();
    expect(mentionQuery('hello an')).toBeNull();
    expect(insertMention('hello @an', 'Ana', ID)).toBe(`hello @[Ana](user:${ID}) `);
  });
});
