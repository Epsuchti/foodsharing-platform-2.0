import { Signal, signal } from '@angular/core';

export interface LatestWins<S> {
  /** The state last asked for while it is being saved (show it instead of the loaded one), else undefined. */
  readonly draft: Signal<S | undefined>;
  /** True while a save is in flight or queued. */
  readonly busy: Signal<boolean>;
  /** Asks for a new full state: saved after the one in flight; states asked for in between are skipped. */
  push(state: S): void;
  /** Drops the draft (call when fresh data arrived and nothing is saving). */
  clear(): void;
}

/**
 * Saves a full-state setting (e.g. follow switches) without racing: one request at a time, and while one is in
 * flight only the latest asked-for state is kept and sent next, so rapid toggles always end on the last state the
 * user chose. Each toggle derives from {@link LatestWins.draft} (not from data loaded before the first save), so a
 * second switch never sends the first switch's old value again. `done` runs once the queue is empty: `ok` is the
 * result of the last request; on failure the draft is dropped so the loaded state shows again.
 */
export function latestWins<S>(send: (state: S) => Promise<boolean>, done: (ok: boolean) => void = () => undefined): LatestWins<S> {
  const draft = signal<S | undefined>(undefined);
  const busy = signal(false);
  let queued: { state: S } | undefined;

  const drain = async () => {
    busy.set(true);
    let ok = true;
    while (queued) {
      const { state } = queued;
      queued = undefined;
      try {
        ok = await send(state);
      } catch {
        ok = false;
      }
    }
    busy.set(false);
    if (!ok) draft.set(undefined);
    done(ok);
  };

  return {
    draft: draft.asReadonly(),
    busy: busy.asReadonly(),
    push(state: S) {
      draft.set(state);
      queued = { state };
      if (!busy()) void drain();
    },
    clear() {
      if (!busy()) draft.set(undefined);
    },
  };
}
