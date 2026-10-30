import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Petition } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { safePetitionHref } from '../petition.resource';

/** Petition notice (C8): call to sign with signatures and days left, sign link, details link and dismiss. */
@Component({
  selector: 'fs-petition-notice',
  imports: [RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = petition();
    <aside
      class="banner"
      [attr.aria-label]="'content.petition.bannerLabel' | t"
      data-testid="content.petition.banner"
    >
      <i class="pi pi-megaphone banner__icon" aria-hidden="true"></i>
      <p class="banner__text">
        <strong>{{ 'content.petition.bannerTitle' | t }}</strong>
        <span data-testid="content.petition.bannerCount">{{
          'content.petition.bannerCount' | t: { signatures: p.signatures, days: p.daysLeft }
        }}</span>
      </p>
      <div class="banner__actions">
        @if (href(); as link) {
          <a
            class="fs-btn fs-btn--sm"
            [href]="link"
            target="_blank"
            rel="noopener noreferrer"
            data-testid="content.petition.bannerSign"
          >
            {{ 'content.petition.sign' | t }}
            <span class="fs-visually-hidden">{{ 'content.petition.newTab' | t }}</span>
          </a>
        }
        <a
          class="fs-btn fs-btn--sm fs-btn--outlined"
          routerLink="/petition"
          data-testid="content.petition.bannerMore"
          >{{ 'content.petition.more' | t }}</a
        >
        <button
          type="button"
          class="banner__close"
          [attr.aria-label]="'content.petition.dismiss' | t"
          [attr.title]="'content.petition.dismiss' | t"
          (click)="dismiss.emit()"
          data-testid="content.petition.bannerDismiss"
        >
          <i class="pi pi-times" aria-hidden="true"></i>
        </button>
      </div>
    </aside>
  `,
  styles: `
    :host {
      display: block;
    }
    .banner {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.75rem 1rem;
      padding: 0.9rem 1.1rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-lg);
      background: var(--fs-warning-soft);
      color: var(--fs-text);
    }
    .banner__icon {
      color: var(--fs-warning-text);
      font-size: 1.25rem;
    }
    .banner__text {
      display: grid;
      flex: 1 1 16rem;
      gap: 0.15rem;
      margin: 0;
    }
    .banner__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.5rem;
    }
    .banner__actions a {
      text-decoration: none;
    }
    .banner__close {
      display: grid;
      place-items: center;
      width: 2.5rem;
      height: 2.5rem;
      border: 0;
      border-radius: var(--fs-radius-sm);
      background: transparent;
      color: var(--fs-text-muted);
      cursor: pointer;
    }
    .banner__close:hover {
      background: var(--fs-surface-muted);
      color: var(--fs-text);
    }
  `,
})
export class PetitionNoticeComponent {
  readonly petition = input.required<Petition>();
  readonly dismiss = output<void>();
  protected readonly href = computed(() => safePetitionHref(this.petition().href));
}
