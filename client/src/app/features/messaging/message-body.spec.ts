import { bodySegments, decodeMentions, encodeMentions, insertMention, mentionQuery, mentionToken } from './message-body';

const ID = '00000000-0000-4000-8000-000000000042';
const ID2 = '00000000-0000-4000-8000-000000000043';

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
    // decision 159: the composer shows the plain name, the token is built on send
    expect(insertMention('hello @an', 'Ana')).toBe('hello @Ana ');
    expect(insertMention('@', 'A [b]')).toBe('@A b ');
    expect(insertMention('price $& @x', 'Ana $1')).toBe('price $& @Ana $1 ');
  });

  it('encodes the picked @Name as tokens on send, longest names first', () => {
    const picked = new Map([
      ['Ana', ID],
      ['Ana Muster', ID2],
    ]);
    expect(encodeMentions('Hi @Ana Muster and @Ana!', picked)).toBe(
      `Hi @[Ana Muster](user:${ID2}) and @[Ana](user:${ID})!`,
    );
    expect(encodeMentions('@Ana', picked)).toBe(`@[Ana](user:${ID})`);
    expect(encodeMentions('(@Ana), @Ana.', picked)).toBe(`(@[Ana](user:${ID})), @[Ana](user:${ID}).`);
    // only at a word start and up to a word end; a hand-edited name stays plain text
    expect(encodeMentions('mail@Ana @Anabel @Anna', picked)).toBe('mail@Ana @Anabel @Anna');
    expect(encodeMentions('no names', new Map())).toBe('no names');
    // names with regex characters are matched literally
    expect(encodeMentions('@Dr. (Bob)?', new Map([['Dr. (Bob)', ID]]))).toBe(`@[Dr. (Bob)](user:${ID})?`);
  });

  it('decodes tokens to @Name for editing, and encoding gives the same body back', () => {
    const body = `Hi @[Ana Muster](user:${ID2}), see https://example.org/x. @[Ana](user:${ID})`;
    const { text, picked } = decodeMentions(body);
    expect(text).toBe('Hi @Ana Muster, see https://example.org/x. @Ana');
    expect([...picked]).toEqual([
      ['Ana Muster', ID2],
      ['Ana', ID],
    ]);
    expect(encodeMentions(text, picked)).toBe(body);
    expect(decodeMentions('plain')).toEqual({ text: 'plain', picked: new Map() });
  });
});
