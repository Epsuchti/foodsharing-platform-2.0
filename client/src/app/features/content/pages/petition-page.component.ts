import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { Petition, PublicContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AnimatedCounterComponent } from '../../../shared/animated-counter/animated-counter.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { loadPetition, safePetitionHref } from '../petition.resource';

/**
 * /petition (C8, public): the running petition with its signature count, the days left and the link to sign it.
 * PETITION_BANNER off → not found; no petition running → empty state.
 */
@Component({
  selector: 'fs-petition-page',
  imports: [...DATA_STATE_IMPORTS, AnimatedCounterComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.petition.title' | t"
      [subtitle]="'content.petition.subtitle' | t"
      icon="pi pi-megaphone"
    />
    <fs-data-state
      [state]="petition"
      skeleton="card"
      backLink="/"
      emptyIcon="pi pi-megaphone"
      [emptyTitle]="'content.petition.inactive' | t"
      data-testid="content.petition.state"
    >
      <ng-template [fsData]="petition" let-p>
        @if (p) {
          <section class="fs-card card" data-testid="content.petition.card">
            <div class="figures">
              <p class="figure">
                <fs-animated-counter
                  class="figure__value"
                  [value]="p.signatures"
                  testId="content.petition.signatures"
                />
                <span class="figure__label">{{
                  'content.petition.signaturesLabel' | t: { count: p.signatures }
                }}</span>
              </p>
              <p class="figure">
                <span class="figure__value" data-testid="content.petition.daysLeft">{{
                  p.daysLeft
                }}</span>
                <span class="figure__label">{{
                  'content.petition.daysLeftLabel' | t: { count: p.daysLeft }
                }}</span>
              </p>
            </div>
            <p class="text">{{ 'content.petition.text' | t }}</p>
            @if (href(p); as link) {
              <a
                class="fs-btn sign"
                [href]="link"
                target="_blank"
                rel="noopener noreferrer"
                data-testid="content.petition.sign"
              >
                <i class="pi pi-pencil" aria-hidden="true"></i
                ><span class="sign__label">{{ 'content.petition.sign' | t }}</span>
                <span class="fs-visually-hidden">{{ 'content.petition.newTab' | t }}</span>
              </a>
            }
          </section>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 44rem);
      margin: 0 auto;
    }
    .card {
      display: grid;
      gap: 1.25rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2rem);
    }
    .figures {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem 2.5rem;
    }
    .figure {
      display: grid;
      gap: 0.15rem;
      margin: 0;
    }
    .figure__value {
      color: var(--fs-primary-strong);
      font-size: clamp(1.8rem, 1.3rem + 2vw, 2.6rem);
      font-weight: 800;
      line-height: 1.1;
      font-variant-numeric: tabular-nums;
    }
    .figure__label {
      color: var(--fs-text-muted);
      font-weight: 600;
    }
    .text {
      margin: 0;
    }
    .sign {
      justify-self: start;
      gap: 0.5rem;
      max-width: 100%;
      text-decoration: none;
      white-space: normal;
    }
    .sign__label {
      min-width: 0;
      overflow-wrap: anywhere;
    }
  `,
})
export class PetitionPageComponent {
  private readonly api = inject(PublicContentService);

  protected readonly petition = loadResource<Petition | null>({
    loader: () => loadPetition(this.api),
    isEmpty: (value) => !value,
  });

  protected readonly href = (p: Petition) => safePetitionHref(p.href);
}
