/**
 * Minimal STOMP 1.2 framing for the one thing the client does (A32): CONNECT, SUBSCRIBE to `/user/queue/events`,
 * receive MESSAGE frames, exchange heart-beats. Hand-written (≈60 lines) instead of `@stomp/stompjs` to keep the
 * start-up bundle lean; the server is Spring's simple broker.
 */
export interface StompFrame {
  command: string;
  headers: Record<string, string>;
  body: string;
}

const NUL = '\0';

/** Serialises a client frame (header values are escaped per STOMP 1.2). */
export function encodeFrame(command: string, headers: Record<string, string>, body = ''): string {
  const lines = Object.entries(headers).map(([key, value]) => `${escape(key)}:${escape(value)}`);
  return `${command}\n${lines.join('\n')}${lines.length ? '\n' : ''}\n${body}${NUL}`;
}

/**
 * Splits buffered socket text into complete frames. Heart-beats (bare EOLs between frames) are dropped; the
 * unfinished tail is returned for the next chunk.
 */
export function parseFrames(buffer: string): { frames: StompFrame[]; rest: string } {
  const frames: StompFrame[] = [];
  let rest = buffer;
  for (let end = rest.indexOf(NUL); end >= 0; end = rest.indexOf(NUL)) {
    const raw = rest.slice(0, end).replace(/^[\r\n]+/, '');
    rest = rest.slice(end + 1);
    if (raw) {
      frames.push(parseFrame(raw));
    }
  }
  return { frames, rest: rest.replace(/^[\r\n]+/, '') };
}

function parseFrame(raw: string): StompFrame {
  const split = raw.search(/\r?\n\r?\n/);
  const head = split >= 0 ? raw.slice(0, split) : raw;
  const body = split >= 0 ? raw.slice(split).replace(/^\r?\n\r?\n/, '') : '';
  const [command, ...headerLines] = head.split(/\r?\n/);
  const headers: Record<string, string> = {};
  for (const line of headerLines) {
    const colon = line.indexOf(':');
    if (colon > 0) {
      const key = unescape(line.slice(0, colon));
      // the first occurrence of a repeated header wins (STOMP 1.2)
      headers[key] ??= unescape(line.slice(colon + 1));
    }
  }
  return { command, headers, body };
}

function escape(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/:/g, '\\c');
}

function unescape(value: string): string {
  return value.replace(/\\([\\rnc])/g, (_, c: string) => ({ '\\': '\\', r: '\r', n: '\n', c: ':' })[c] ?? c);
}

/** Reconnect delay: exponential 1 s → 30 s with ±20 % jitter (A32). */
export function backoffDelay(attempt: number, random: () => number = Math.random): number {
  const base = Math.min(30_000, 1000 * 2 ** Math.max(0, attempt));
  return Math.round(base * (0.8 + random() * 0.4));
}
