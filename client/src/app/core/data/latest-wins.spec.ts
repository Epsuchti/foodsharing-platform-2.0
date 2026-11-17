import { vi } from 'vitest';

import { latestWins } from './latest-wins';

/** A send whose requests the test answers one by one, in order. */
function controlledSend<S>() {
  const sent: S[] = [];
  const answers: ((ok: boolean) => void)[] = [];
  const send = vi.fn((state: S) => {
    sent.push(state);
    return new Promise<boolean>((resolve) => answers.push(resolve));
  });
  const answer = async (ok = true) => {
    answers.shift()!(ok);
    await Promise.resolve();
    await Promise.resolve();
  };
  return { send, sent, answer };
}

interface Follow {
  bell: boolean;
  mail: boolean;
}

describe('latestWins', () => {
  it('sends one request at a time and ends on the last state asked for', async () => {
    const { send, sent, answer } = controlledSend<Follow>();
    const done = vi.fn();
    const saver = latestWins(send, done);

    saver.push({ bell: true, mail: false });
    // toggled twice more while the first save is in flight: only the latest is sent next
    saver.push({ bell: true, mail: true });
    saver.push({ bell: false, mail: true });
    expect(sent).toEqual([{ bell: true, mail: false }]);
    expect(saver.busy()).toBe(true);
    expect(saver.draft()).toEqual({ bell: false, mail: true });

    await answer();
    expect(sent).toEqual([{ bell: true, mail: false }, { bell: false, mail: true }]);
    expect(done).not.toHaveBeenCalled();

    await answer();
    expect(sent).toHaveLength(2);
    expect(saver.busy()).toBe(false);
    expect(done).toHaveBeenCalledExactlyOnceWith(true);
    // the draft stays until fresh data arrived
    expect(saver.draft()).toEqual({ bell: false, mail: true });
    saver.clear();
    expect(saver.draft()).toBeUndefined();
  });

  it('keeps the draft while saving, even if asked to clear', async () => {
    const { send, answer } = controlledSend<boolean>();
    const saver = latestWins(send);
    saver.push(true);
    saver.clear();
    expect(saver.draft()).toBe(true);
    await answer();
  });

  it('drops the draft when the last save failed, but still sends a newer state after a failure', async () => {
    const { send, sent, answer } = controlledSend<boolean>();
    const done = vi.fn();
    const saver = latestWins(send, done);

    saver.push(true);
    saver.push(false);
    await answer(false);
    expect(sent).toEqual([true, false]);
    await answer(true);
    expect(done).toHaveBeenCalledExactlyOnceWith(true);
    expect(saver.draft()).toBe(false);

    saver.push(true);
    await answer(false);
    expect(done).toHaveBeenLastCalledWith(false);
    expect(saver.draft()).toBeUndefined();
  });

  it('treats a throwing send as a failed save', async () => {
    const done = vi.fn();
    const saver = latestWins(() => Promise.reject(new Error('offline')), done);
    saver.push(true);
    await vi.waitFor(() => expect(done).toHaveBeenCalledWith(false));
    expect(saver.busy()).toBe(false);
  });
});
