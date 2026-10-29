import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';

import { NewsletterKind, NewsletterPage, NewsletterStatus, NewslettersService, Role } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RegionContextService } from '../../../core/region/region-context.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/** Scope value for the global (ORGA) newsletter. */
export const GLOBAL_SCOPE = 'GLOBAL';

/**
 * `/newsletters` and `/regions/:regionId/newsletters` (N7): newsletters of a scope (global for ORGA, regions the
 * user administrates), status filter, "New newsletter" creates a draft and opens the editor. ORGA picks the kind in
 * the global scope (newsletter to opted-in members or info mail to everyone who kept `infoMailMessage`, A20); the
 * kind is fixed at creation because `NewsletterPatch` has no `kind`.
 */
@Component({
  selector: 'fs-newsletter-list-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, PageHeaderComponent, RouterLink, Select, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'newsletters.list.title' | t" [subtitle]="'newsletters.list.subtitle' | t" icon="pi pi-megaphone">
      <p-button
        [label]="'newsletters.list.create' | t"
        icon="pi pi-plus"
        size="small"
        [disabled]="!scopes().length"
        [loading]="create.busy()"
        (onClick)="create.run()"
        data-testid="newsletters.list.create"
      />
    </fs-page-header>
    <div class="filters">
      <div class="field">
        <label for="nl-scope">{{ 'newsletters.list.scope' | t }}</label>
        <p-select
          inputId="nl-scope"
          [options]="scopes()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="scope()"
          (ngModelChange)="chosen.set($event)"
          data-testid="newsletters.list.scope"
        />
      </div>
      @if (kindChoice()) {
        <div class="field">
          <label for="nl-kind">{{ 'newsletters.list.kind' | t }}</label>
          <p-select
            inputId="nl-kind"
            [options]="kinds()"
            optionLabel="label"
            optionValue="value"
            [ngModel]="kind()"
            (ngModelChange)="kind.set($event)"
            data-testid="newsletters.list.kind"
          />
        </div>
      }
      <div class="field">
        <label for="nl-status">{{ 'newsletters.list.status' | t }}</label>
        <p-select
          inputId="nl-status"
          [options]="statuses()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="status()"
          (ngModelChange)="status.set($event)"
          data-testid="newsletters.list.filter"
        />
      </div>
    </div>
    <fs-data-state
      [state]="list"
      skeleton="list"
      [skeletonRows]="4"
      emptyIcon="pi pi-megaphone"
      [emptyTitle]="'newsletters.list.empty' | t"
      data-testid="newsletters.list.state"
    >
      <ng-template [fsData]="list" let-page>
        <ul class="list">
          @for (nl of page.items; track nl.id) {
            <li class="fs-card item" data-testid="newsletters.list.item" [attr.data-status]="nl.status" [attr.data-id]="nl.id">
              <a [routerLink]="['/newsletters', nl.id]" class="subject" data-testid="newsletters.list.open">{{ nl.subject || ('newsletters.list.untitled' | t) }}</a>
              @if (nl.kind === 'INFO_MAIL') {
                <span class="badge badge--INFO_MAIL" data-testid="newsletters.list.infoMail">{{ 'newsletters.kind.INFO_MAIL' | t }}</span>
              }
              <span [class]="'badge badge--' + nl.status">{{ 'newsletters.status.' + nl.status | t }}</span>
              <span class="muted">
                {{ nl.region?.name ?? ('newsletters.list.global' | t) }} ·
                {{ (nl.sentAt ?? nl.queuedAt ?? nl.createdAt) | fsDate: 'dateTime' }}
                @if (nl.recipientCount !== undefined && nl.recipientCount !== null) {
                  · {{ 'newsletters.list.recipients' | t: { count: nl.recipientCount } }}
                }
              </span>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../newsletters.scss',
})
export class NewsletterListPageComponent {
  private readonly api = inject(NewslettersService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);
  private readonly context = inject(RegionContextService);
  private readonly router = inject(Router);

  /** `/regions/:regionId/newsletters` preselects that region. */
  readonly regionId = input<string | undefined>(undefined);

  protected readonly scopes = computed(() => {
    this.i18n.language();
    const options: { value: string; label: string }[] = [];
    if (this.session.user()?.role === Role.Orga) {
      options.push({ value: GLOBAL_SCOPE, label: this.i18n.t('newsletters.list.global') });
    }
    for (const region of this.context.regions().filter((r) => r.admin)) {
      options.push({ value: region.id, label: region.name });
    }
    const preset = this.regionId();
    if (preset && !options.some((o) => o.value === preset)) {
      options.push({ value: preset, label: this.i18n.t('newsletters.list.thisRegion') });
    }
    return options;
  });

  protected readonly chosen = signal<string | null>(null);
  protected readonly scope = computed(() => this.chosen() ?? this.regionId() ?? this.scopes()[0]?.value ?? GLOBAL_SCOPE);
  protected readonly status = signal<NewsletterStatus | 'ALL'>('ALL');

  protected readonly statuses = computed(() => {
    this.i18n.language();
    return (['ALL', ...Object.values(NewsletterStatus)] as const).map((value) => ({
      value,
      label: this.i18n.t(`newsletters.status.${value}`),
    }));
  });

  protected readonly list = loadResource<NewsletterPage, { scope: string; status: NewsletterStatus | 'ALL' }>({
    params: () => ({ scope: this.scope(), status: this.status() }),
    loader: ({ scope, status }) =>
      this.api.listNewsletters({
        regionId: scope === GLOBAL_SCOPE ? undefined : scope,
        status: status === 'ALL' ? undefined : status,
        size: 50,
      }),
    isEmpty: (page) => page.items.length === 0,
  });

  /** Info mails (A20) are global and ORGA only: the kind select shows for ORGA in the global scope. */
  protected readonly kindChoice = computed(() => this.session.user()?.role === Role.Orga && this.scope() === GLOBAL_SCOPE);
  protected readonly kind = signal<NewsletterKind>(NewsletterKind.Newsletter);
  protected readonly kinds = computed(() => {
    this.i18n.language();
    return Object.values(NewsletterKind).map((value) => ({ value, label: this.i18n.t(`newsletters.kind.${value}`) }));
  });

  protected readonly create = runMutation(
    () =>
      this.api.createNewsletter({
        newsletterCreate: {
          kind: this.kindChoice() ? this.kind() : NewsletterKind.Newsletter,
          regionId: this.scope() === GLOBAL_SCOPE ? undefined : this.scope(),
          subject: this.i18n.t('newsletters.list.newSubject'),
          bodyMarkdown: this.i18n.t('newsletters.list.newBody'),
        },
      }),
    { onSuccess: (created) => void this.router.navigate(['/newsletters', created.id]) },
  );
}
