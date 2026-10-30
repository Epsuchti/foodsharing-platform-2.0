import { Injectable, signal } from '@angular/core';

import { readStorage, writeStorage } from '../../core/storage';

export const RELEASE_NOTES_SEEN_KEY = 'fs.releaseNotesSeen';

/**
 * The newest release-note version the visitor has seen (C4), kept in `localStorage['fs.releaseNotesSeen']` (per
 * browser, also for visitors). The release-notes page marks the newest version seen; the header dot compares.
 */
@Injectable({ providedIn: 'root' })
export class ReleaseNotesSeenService {
  private readonly state = signal<string | null>(readStorage(RELEASE_NOTES_SEEN_KEY));
  readonly seen = this.state.asReadonly();

  markSeen(version: string): void {
    this.state.set(version);
    writeStorage(RELEASE_NOTES_SEEN_KEY, version);
  }
}
