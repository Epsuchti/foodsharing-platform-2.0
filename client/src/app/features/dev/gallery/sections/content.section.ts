import { ChangeDetectionStrategy, Component, Type } from '@angular/core';

import {
  LearningChapter,
  Petition,
  PublicStatistics,
  ReleaseNote,
  TeamGroup,
} from '../../../../api';
import { LearningChapterItemComponent } from '../../../content/components/learning-chapter-item.component';
import { PetitionNoticeComponent } from '../../../content/components/petition-notice.component';
import { ReleaseNoteCardComponent } from '../../../content/components/release-note-card.component';
import { StatisticsTilesComponent } from '../../../content/components/statistics-tiles.component';
import { TeamGroupCardComponent } from '../../../content/components/team-group-card.component';

/**
 * Content (C1, C3, C4, C8, C16): learning chapter items in their three states, the statistics counters, a team
 * group, release notes (new and seen) and the petition notice.
 */
@Component({
  selector: 'fs-gallery-content-section',
  imports: [
    LearningChapterItemComponent,
    PetitionNoticeComponent,
    ReleaseNoteCardComponent,
    StatisticsTilesComponent,
    TeamGroupCardComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-content">Content</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.learningChapters">
        <header class="specimen__head"><code>fs-learning-chapter-item</code></header>
        <div style="display: grid; gap: 0.75rem">
          <fs-learning-chapter-item [chapter]="chapters[0]" [position]="1" state="read" />
          <fs-learning-chapter-item [chapter]="chapters[1]" [position]="2" state="next" />
          <fs-learning-chapter-item [chapter]="chapters[2]" [position]="3" state="open" />
        </div>
      </section>
      <section class="specimen" style="grid-column: 1 / -1" data-testid="gallery.statisticsTiles">
        <header class="specimen__head"><code>fs-statistics-tiles</code></header>
        <fs-statistics-tiles [stats]="stats" />
      </section>
      <section class="specimen" style="grid-column: 1 / -1" data-testid="gallery.teamGroup">
        <header class="specimen__head"><code>fs-team-group-card</code></header>
        <div style="display: grid; gap: 0.75rem">
          <fs-team-group-card [group]="team[0]" />
          <fs-team-group-card [group]="team[1]" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.releaseNotes">
        <header class="specimen__head"><code>fs-release-note-card</code> new / seen</header>
        <div style="display: grid; gap: 0.75rem">
          <fs-release-note-card [note]="notes[0]" [isNew]="true" />
          <fs-release-note-card [note]="notes[1]" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.petitionNotice">
        <header class="specimen__head"><code>fs-petition-notice</code></header>
        <fs-petition-notice [petition]="petition" />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class ContentGallerySection {
  protected readonly chapters: LearningChapter[] = [
    {
      key: 'rules',
      title: 'Die Regeln',
      summary: 'Was wir versprechen und warum.',
      estimatedMinutes: 2,
      sortOrder: 1,
    },
    {
      key: 'hygiene',
      title: 'Hygiene',
      summary: 'Kühlkette, Haltbarkeit, saubere Hände.',
      estimatedMinutes: 2,
      sortOrder: 2,
    },
    {
      key: 'how-a-pickup-works',
      title: 'So läuft eine Abholung',
      estimatedMinutes: 3,
      sortOrder: 3,
    },
  ];

  protected readonly stats: PublicStatistics = {
    kgSaved: 1284500,
    pickups: 96420,
    foodsavers: 4210,
    stores: 812,
    cooperations: 655,
    foodSharePoints: 64,
    baskets: 12870,
    asOf: '2026-11-01T02:00:00Z',
  };

  protected readonly team: TeamGroup[] = [
    {
      id: 'gallery-it',
      name: 'IT',
      teaser: 'Entwickelt und betreibt die Plattform.',
      members: [
        { displayName: 'Anna Keller', roleDescription: 'Koordination' },
        { displayName: 'Luca Rossi' },
        { displayName: 'Mia Steiner', roleDescription: 'Support' },
      ],
    },
    {
      id: 'gallery-pr',
      name: 'Öffentlichkeitsarbeit',
      teaser: 'Presse, Social Media und Kampagnen.',
      members: [],
    },
  ];

  protected readonly notes: ReleaseNote[] = [
    {
      id: 'gallery-5',
      version: '5.0.0',
      releasedAt: '2026-10-26',
      title: 'Gemeinsam reden und entscheiden',
      body: 'Chats, Forum, Termine, Abstimmungen und **Umfragen**.',
    },
    {
      id: 'gallery-4',
      version: '4.0.0',
      releasedAt: '2026-08-31',
      title: 'Automatisch zuverlässig',
      body: 'Erinnerungen, Springer-Aufrufe und Abholregeln.',
    },
  ];

  protected readonly petition: Petition = {
    href: 'https://petition.local.test/foodsharing',
    signatures: 4213,
    daysLeft: 21,
  };
}

/** Gallery sections of the content feature (one file per module, no conflicts). */
export const CONTENT_GALLERY_SECTIONS: readonly Type<unknown>[] = [ContentGallerySection];
