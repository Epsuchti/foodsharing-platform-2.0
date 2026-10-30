import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { DonationPage, Language, PublicContentService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import {
  DonationFrameKind,
  donationFrames,
  donationPageEmpty,
  safeHttpsUrl,
} from '../donation.util';

interface ShownFrame {
  kind: DonationFrameKind;
  url: string;
  src: SafeResourceUrl;
}

/**
 * /donate (C7, public): the running campaign (published campaign pages, when `showCampaignCard`), the donation forms
 * of the payment provider as iframes (one-time, campaign, friendship circle, self service — https only; the CSP
 * `frame-src` lists their hosts, decision 97) and the link to more information. Configured by ORGA
 * (`updateDonationSettings`).
 */
@Component({
  selector: 'fs-donate-page',
  imports: [...DATA_STATE_IMPORTS, MarkdownComponent, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'content.donate.title' | t"
      [subtitle]="'content.donate.subtitle' | t"
      icon="pi pi-heart-fill"
    />
    <fs-data-state
      [state]="page"
      skeleton="detail"
      emptyIcon="pi pi-heart"
      [emptyTitle]="'content.donate.empty' | t"
      [emptyText]="'content.donate.emptyText' | t"
      backLink="/"
      data-testid="content.donate.state"
    >
      <ng-template [fsData]="page" let-p>
        <div class="donate">
          @if (p.showCampaignCard) {
            @for (campaign of p.campaignPages; track campaign.slug) {
              <article
                class="fs-card campaign"
                [attr.data-slug]="campaign.slug"
                data-testid="content.donate.campaign"
              >
                <h2 class="campaign__title">
                  <i class="pi pi-megaphone" aria-hidden="true"></i>{{ campaign.title }}
                </h2>
                <fs-markdown [markdown]="campaign.body" />
              </article>
            }
          }
          @for (frame of frames(); track frame.kind) {
            <section
              class="fs-card frame"
              [attr.data-kind]="frame.kind"
              data-testid="content.donate.frame"
            >
              <h2 class="frame__title">{{ 'content.donate.frame.' + frame.kind | t }}</h2>
              <iframe
                [src]="frame.src"
                [title]="'content.donate.frame.' + frame.kind | t"
                loading="lazy"
                referrerpolicy="strict-origin-when-cross-origin"
                [attr.data-src]="frame.url"
                data-testid="content.donate.iframe"
              ></iframe>
            </section>
          }
          @if (infoUrl(); as href) {
            <p class="info">
              <a [href]="href" target="_blank" rel="noopener" data-testid="content.donate.info">
                <i class="pi pi-external-link" aria-hidden="true"></i>{{ 'content.donate.info' | t }}
              </a>
            </p>
          }
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 52rem);
      margin: 0 auto;
    }
    .donate {
      display: grid;
      gap: 1.25rem;
    }
    .campaign {
      display: grid;
      gap: 0.75rem;
      padding: clamp(1.1rem, 0.6rem + 2vw, 2rem);
      border-left: 4px solid var(--fs-primary);
    }
    .campaign__title,
    .frame__title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      font-size: 1.15rem;
    }
    .campaign__title i {
      color: var(--fs-primary);
    }
    .frame {
      display: grid;
      gap: 0.75rem;
      padding: clamp(1rem, 0.6rem + 1.5vw, 1.5rem);
    }
    iframe {
      width: 100%;
      min-height: 32rem;
      border: 1px solid var(--fs-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-surface);
    }
    .info {
      margin: 0;
    }
    .info a {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
  `,
})
export class DonatePageComponent {
  private readonly api = inject(PublicContentService);
  private readonly i18n = inject(I18nService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly page = loadResource<DonationPage, string>({
    params: () => this.i18n.language(),
    loader: (lang) => this.api.getDonationPage({ lang: lang as Language }),
    isEmpty: donationPageEmpty,
  });

  protected readonly frames = computed<ShownFrame[]>(() => {
    const page = this.page.data();
    return page
      ? donationFrames(page).map((frame) => ({
          ...frame,
          // https only (donationFrames): the URL is ORGA configuration, not user input
          src: this.sanitizer.bypassSecurityTrustResourceUrl(frame.url),
        }))
      : [];
  });

  protected readonly infoUrl = computed(() => safeHttpsUrl(this.page.data()?.infoUrl));
}
