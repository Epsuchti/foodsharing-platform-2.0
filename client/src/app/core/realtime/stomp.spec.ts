import { backoffDelay, encodeFrame, parseFrames } from './stomp';

describe('stomp framing', () => {
  it('encodes frames with escaped headers and a NUL terminator', () => {
    expect(encodeFrame('SUBSCRIBE', { id: 'a', destination: '/user/queue/events' })).toBe(
      'SUBSCRIBE\nid:a\ndestination:/user/queue/events\n\n\0',
    );
    expect(encodeFrame('SEND', { k: 'a:b\nc' }, 'x')).toBe('SEND\nk:a\\cb\\nc\n\nx\0');
  });

  it('parses complete frames, drops heart-beats and keeps the unfinished tail', () => {
    const chunk =
      '\nCONNECTED\nversion:1.2\n\n\0\n\nMESSAGE\ndestination:/user/queue/events\n\n{"type":"PING"}\0MESS';
    const { frames, rest } = parseFrames(chunk);
    expect(frames.map((f) => f.command)).toEqual(['CONNECTED', 'MESSAGE']);
    expect(frames[0].headers['version']).toBe('1.2');
    expect(frames[1].body).toBe('{"type":"PING"}');
    expect(rest).toBe('MESS');
    expect(parseFrames(rest + 'AGE\n\nbody\0').frames[0]).toEqual({ command: 'MESSAGE', headers: {}, body: 'body' });
  });

  it('backs off exponentially from 1 s to 30 s with jitter', () => {
    const mid = () => 0.5;
    expect([0, 1, 2, 3, 4, 5, 9].map((attempt) => backoffDelay(attempt, mid))).toEqual([
      1000, 2000, 4000, 8000, 16000, 30000, 30000,
    ]);
    expect(backoffDelay(0, () => 0)).toBe(800);
    expect(backoffDelay(0, () => 1)).toBe(1200);
  });
});
