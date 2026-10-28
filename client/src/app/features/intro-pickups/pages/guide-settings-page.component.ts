import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';

import { FeedLink, GuideProfile, IntroPickupCategory, IntroPickupGuideService, PrivateInfoTemplate, RegionRef } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * `/guide/settings` (EA11, EA15, EA16): own calendar (iCal link) for date suggestions, phone shown to booked
 * newcomers, the personal EinAb feed for calendar apps, private info templates and the regions the guide works in.
 */
@Component({
  selector: 'fs-guide-settings-page',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, PageHeaderComponent, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.settings.title' | t" [subtitle]="'introPickups.settings.subtitle' | t" icon="pi pi-cog" backLink="/guide" [backLabel]="'introPickups.guide.title' | t" />
    <div class="columns">
      <section class="fs-card card">
        <h2 class="card__title"><i class="pi pi-user" aria-hidden="true"></i>{{ 'introPickups.settings.profile' | t }}</h2>
        <fs-data-state [state]="profile" skeleton="form" [skeletonRows]="2" [compact]="true" data-testid="introPickups.settings.profileState">
          <ng-template [fsData]="profile">
            <form class="form" (ngSubmit)="save.run()">
              <div class="field">
                <label for="gs-ical">{{ 'introPickups.settings.icalLink' | t }}</label>
                <input pInputText id="gs-ical" name="icalLink" type="url" [(ngModel)]="icalLink" placeholder="https://…/calendar.ics" data-testid="introPickups.settings.icalLink" />
                <span class="muted">{{ 'introPickups.settings.icalHint' | t }}</span>
              </div>
              <div class="field">
                <label for="gs-phone">{{ 'introPickups.settings.phone' | t }}</label>
                <input pInputText id="gs-phone" name="phone" type="tel" [(ngModel)]="phone" placeholder="+41 79 …" data-testid="introPickups.settings.phone" />
                <span class="muted">{{ 'introPickups.settings.phoneHint' | t }}</span>
              </div>
              <div class="actions">
                <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [loading]="save.busy()" data-testid="introPickups.settings.save" />
              </div>
            </form>
          </ng-template>
        </fs-data-state>
      </section>

      <section class="fs-card card">
        <h2 class="card__title"><i class="pi pi-calendar" aria-hidden="true"></i>{{ 'introPickups.settings.feed' | t }}</h2>
        <p class="muted">{{ 'introPickups.settings.feedText' | t }}</p>
        <fs-data-state [state]="feed" skeleton="form" [skeletonRows]="1" [compact]="true" data-testid="introPickups.settings.feedState">
          <ng-template [fsData]="feed" let-link>
            <div class="feed">
              <input pInputText readonly [value]="link.url" [attr.aria-label]="'introPickups.settings.feed' | t" data-screenshot-mask data-testid="introPickups.settings.feedUrl" />
              <p-button icon="pi pi-copy" [label]="'introPickups.settings.copy' | t" [outlined]="true" (onClick)="copy(link.url)" data-testid="introPickups.settings.copy" />
            </div>
            <a [href]="webcal(link.url)" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.settings.subscribe"><i class="pi pi-calendar-plus" aria-hidden="true"></i>{{ 'introPickups.settings.subscribe' | t }}</a>
          </ng-template>
        </fs-data-state>
      </section>

      <section class="fs-card card">
        <h2 class="card__title"><i class="pi pi-file" aria-hidden="true"></i>{{ 'introPickups.settings.templates' | t }}</h2>
        <fs-data-state [state]="templates" skeleton="list" [skeletonRows]="2" [compact]="true" emptyIcon="pi pi-file" [emptyTitle]="'introPickups.settings.noTemplates' | t" [emptyText]="'introPickups.settings.noTemplatesText' | t" data-testid="introPickups.settings.templatesState">
          <ng-template [fsData]="templates" let-list>
            <ul class="list">
              @for (tpl of list; track tpl.category) {
                <li class="tpl" [attr.data-category]="tpl.category" data-testid="introPickups.settings.template">
                  <span class="tpl__main">
                    <strong>{{ 'common.introCategory.' + tpl.category | t }}</strong>
                    <span class="muted">{{ tpl.content }}</span>
                  </span>
                  <p-button icon="pi pi-trash" [ariaLabel]="'introPickups.settings.deleteTemplate' | t" [text]="true" severity="danger" size="small" [loading]="removeTemplate.busy()" (onClick)="removeTemplate.run(tpl.category)" data-testid="introPickups.settings.deleteTemplate" />
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </section>

      <section class="fs-card card">
        <h2 class="card__title"><i class="pi pi-map" aria-hidden="true"></i>{{ 'introPickups.settings.regions' | t }}</h2>
        <fs-data-state [state]="regions" skeleton="list" [skeletonRows]="1" [compact]="true" emptyIcon="pi pi-map" [emptyTitle]="'introPickups.guide.noRegions' | t" data-testid="introPickups.settings.regionsState">
          <ng-template [fsData]="regions" let-list>
            <ul class="tags" data-testid="introPickups.settings.regionList">
              @for (region of list; track region.id) {
                <li class="tag tag--ok">{{ region.name }}</li>
              }
            </ul>
            <p class="muted">{{ 'introPickups.settings.regionsText' | t }}</p>
          </ng-template>
        </fs-data-state>
      </section>
    </div>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 24rem), 1fr));
      gap: 1rem;
      align-items: start;
    }
    .feed {
      display: flex;
      gap: 0.5rem;
    }
    .feed input {
      flex: 1;
      min-width: 0;
    }
    .tags {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .tpl {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.5rem;
      padding: 0.5rem 0;
      border-top: 1px solid var(--fs-border);
    }
    .tpl__main {
      display: grid;
      min-width: 0;
      white-space: pre-line;
      overflow-wrap: anywhere;
    }
  `,
})
export class GuideSettingsPageComponent {
  private readonly api = inject(IntroPickupGuideService);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18nService);

  protected icalLink = '';
  protected phone = '';

  protected readonly profile = loadResource<GuideProfile>({ loader: () => this.api.getGuideProfile(), isEmpty: () => false });
  protected readonly feed = loadResource<FeedLink>({ loader: () => this.api.getGuideFeedLink(), isEmpty: () => false });
  protected readonly templates = loadResource<PrivateInfoTemplate[]>({ loader: () => this.api.listPrivateInfoTemplates() });
  protected readonly regions = loadResource<RegionRef[]>({ loader: () => this.api.listGuideRegions() });
  private readonly loaded = signal(false);

  constructor() {
    effect(() => {
      const profile = this.profile.data();
      if (profile && !this.loaded()) {
        this.icalLink = profile.icalLink ?? '';
        this.phone = profile.phone ?? '';
        this.loaded.set(true);
      }
    });
  }

  protected readonly save = runMutation(
    () =>
      this.api.updateGuideProfile({
        guideProfileUpdate: { icalLink: this.icalLink.trim(), phone: this.phone.trim() },
      }),
    { successToastKey: 'introPickups.settings.saved', onSuccess: () => this.profile.reload() },
  );

  protected readonly removeTemplate = runMutation(
    (category: IntroPickupCategory) => this.api.deletePrivateInfoTemplate({ category }),
    { successToastKey: 'introPickups.settings.templateDeleted', onSuccess: () => this.templates.reload() },
  );

  protected webcal(url: string): string {
    return url.replace(/^https?:/, 'webcal:');
  }

  protected copy(url: string): void {
    void navigator.clipboard?.writeText(url).then(
      () => this.messages.add({ severity: 'success', summary: this.i18n.t('introPickups.settings.copied') }),
      () => undefined,
    );
  }
}
