import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { SelectButton } from '@openng/optimus-ui/selectbutton';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  JobProgress,
  PaperSize,
  PassportRequestView,
  PassportsService,
  RegionMemberPage,
  RegionsService,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ProgressJobComponent } from '../../../shared/progress-job/progress-job.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { downloadUrl } from '../verification.resource';

/**
 * `/regions/:regionId/passports` (Ve3, EA20): ambassadors create passports for members of their region. Open passport
 * requests (pre-selectable), verified members with search and multi-select, options (renew vs. reprint, valid from,
 * A4 sheet or card size, notify by bell/mail), then a server job with progress and the batch PDF as result.
 */
@Component({
  selector: 'fs-region-passports-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    DateTimePipe,
    FormsModule,
    InputText,
    PageHeaderComponent,
    ProgressJobComponent,
    SelectButton,
    TPipe,
    ToggleSwitch,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="'verification.passports.title' | t"
      [subtitle]="'verification.passports.subtitle' | t"
      icon="pi pi-id-card"
      [backLink]="['/regions', regionId(), 'verification']"
      [backLabel]="'verification.passports.backToQueue' | t"
    />

    <section class="fs-card card" data-testid="verification.passports.requests">
      <h2 class="card__title"><i class="pi pi-inbox" aria-hidden="true"></i>{{ 'verification.passports.requestsTitle' | t }}</h2>
      <div class="check">
        <p-checkbox inputId="pp-fulfilled" [binary]="true" [ngModel]="includeFulfilled()" (ngModelChange)="includeFulfilled.set($event)" data-testid="verification.passports.includeFulfilled" />
        <label for="pp-fulfilled">{{ 'verification.passports.includeFulfilled' | t }}</label>
      </div>
      <fs-data-state
        [state]="requests"
        skeleton="list"
        [skeletonRows]="2"
        [compact]="true"
        emptyIcon="pi pi-inbox"
        [emptyTitle]="'verification.passports.noRequests' | t"
        data-testid="verification.passports.requestsState"
      >
        <ng-template [fsData]="requests" let-list>
          <ul class="rows">
            @for (r of list; track r.id) {
              <li class="row" data-testid="verification.passports.request" [attr.data-user]="r.user.id" [attr.data-fulfilled]="!!r.fulfilledAt">
                <p-checkbox [inputId]="'pp-r-' + r.id" [binary]="true" [disabled]="!!r.fulfilledAt" [ngModel]="selected().has(r.user.id)" (ngModelChange)="toggle(r.user.id, $event)" [ariaLabel]="r.user.displayName" />
                <fs-user-chip [name]="r.user.displayName" [userId]="r.user.id" [avatarUrl]="r.user.avatarUrl" />
                <span class="muted">
                  {{ 'verification.passports.requestedAt' | t: { date: (r.requestedAt | fsDate: 'date') } }}
                  @if (r.fulfilledAt) {
                    · {{ 'verification.passports.fulfilledAt' | t: { date: (r.fulfilledAt | fsDate: 'date'), name: r.fulfilledBy?.displayName ?? '–' } }}
                  }
                </span>
              </li>
            }
          </ul>
          <div class="actions">
            <p-button [label]="'verification.passports.selectRequests' | t" icon="pi pi-check-square" size="small" [outlined]="true" severity="secondary" (onClick)="selectRequests(list)" data-testid="verification.passports.selectRequests" />
          </div>
        </ng-template>
      </fs-data-state>
    </section>

    <section class="fs-card card" data-testid="verification.passports.members">
      <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i>{{ 'verification.passports.membersTitle' | t }}</h2>
      <div class="field">
        <label for="pp-search">{{ 'verification.passports.search' | t }}</label>
        <input pInputText id="pp-search" type="search" [ngModel]="search()" (ngModelChange)="search.set($event)" data-testid="verification.passports.search" />
      </div>
      <fs-data-state
        [state]="members"
        skeleton="list"
        [skeletonRows]="4"
        [compact]="true"
        emptyIcon="pi pi-users"
        [emptyTitle]="'verification.passports.noMembers' | t"
        data-testid="verification.passports.membersState"
      >
        <ng-template [fsData]="members" let-page>
          <ul class="rows">
            @for (m of page.items; track m.user.id) {
              <li class="row" data-testid="verification.passports.member" [attr.data-user]="m.user.id">
                <p-checkbox [inputId]="'pp-m-' + m.user.id" [binary]="true" [ngModel]="selected().has(m.user.id)" (ngModelChange)="toggle(m.user.id, $event)" [ariaLabel]="m.user.displayName" />
                <fs-user-chip [name]="m.user.displayName" [userId]="m.user.id" [avatarUrl]="m.user.avatarUrl" [foodsaverNumber]="m.user.foodsaverNumber" [verified]="m.verified" />
              </li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    </section>

    <section class="fs-card card" data-testid="verification.passports.options">
      <h2 class="card__title"><i class="pi pi-cog" aria-hidden="true"></i>{{ 'verification.passports.optionsTitle' | t }}</h2>
      <p class="muted" data-testid="verification.passports.selectedCount">{{ 'verification.passports.selectedCount' | t: { count: selected().size } }}</p>
      <div class="options">
        <div class="check">
          <p-toggleswitch inputId="pp-renew" [ngModel]="renew()" (ngModelChange)="renew.set($event)" data-testid="verification.passports.renew" />
          <label for="pp-renew">{{ 'verification.passports.renew' | t }}</label>
        </div>
        <p class="muted">{{ (renew() ? 'verification.passports.renewHint' : 'verification.passports.reprintHint') | t }}</p>
        @if (renew()) {
          <div class="field">
            <label for="pp-from">{{ 'verification.passports.validFrom' | t }}</label>
            <input pInputText id="pp-from" type="date" [ngModel]="validFrom()" (ngModelChange)="validFrom.set($event)" data-testid="verification.passports.validFrom" />
          </div>
        }
        <div class="field">
          <span id="pp-paper">{{ 'verification.passports.paperSize' | t }}</span>
          <p-selectbutton [options]="paperSizes()" optionLabel="label" optionValue="value" [allowEmpty]="false" [ngModel]="paperSize()" (ngModelChange)="paperSize.set($event)" ariaLabelledBy="pp-paper" data-testid="verification.passports.paperSize" />
        </div>
        <div class="check">
          <p-toggleswitch inputId="pp-notify" [ngModel]="notify()" (ngModelChange)="notify.set($event)" data-testid="verification.passports.notify" />
          <label for="pp-notify">{{ 'verification.passports.notify' | t }}</label>
        </div>
      </div>
      <p class="muted" data-testid="verification.passports.layoutNote"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'verification.passports.layoutNote' | t }}</p>
      <div class="actions">
        <p-button
          [label]="'verification.passports.create' | t"
          icon="pi pi-print"
          [disabled]="selected().size === 0"
          [loading]="create.busy()"
          (onClick)="create.run()"
          data-testid="verification.passports.create"
        />
      </div>
      @if (jobId(); as id) {
        <fs-progress-job [jobId]="id" [label]="'verification.passports.jobLabel' | t" [doneLabel]="'verification.passports.jobDone' | t" (finished)="finished($event)" testId="verification.passports.job" />
      }
      @if (result(); as job) {
        <div class="actions">
          <p-button [label]="'verification.passports.downloadResult' | t" icon="pi pi-download" (onClick)="downloadResult(job)" data-testid="verification.passports.downloadResult" />
        </div>
      }
    </section>
  `,
  styleUrl: '../verification.scss',
  styles: `
    .rows {
      display: grid;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .row {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 0.6rem;
      padding: 0.35rem 0;
      border-bottom: 1px solid var(--fs-border);
    }
    .check {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .options {
      display: grid;
      gap: 0.75rem;
    }
  `,
})
export class RegionPassportsPageComponent {
  private readonly api = inject(PassportsService);
  private readonly regions = inject(RegionsService);
  private readonly i18n = inject(I18nService);
  private readonly document = inject(DOCUMENT);

  readonly regionId = input.required<string>();
  protected readonly includeFulfilled = signal(false);
  protected readonly search = signal('');
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly renew = signal(true);
  protected readonly validFrom = signal('');
  protected readonly paperSize = signal<PaperSize>(PaperSize.A4);
  protected readonly notify = signal(true);
  protected readonly jobId = signal<string | undefined>(undefined);
  protected readonly result = signal<JobProgress | undefined>(undefined);

  protected readonly paperSizes = computed(() => {
    this.i18n.language();
    return Object.values(PaperSize).map((value) => ({ value, label: this.i18n.t(`verification.passports.paper.${value}`) }));
  });

  protected readonly requests = loadResource<PassportRequestView[], { regionId: string; includeFulfilled: boolean }>({
    params: () => ({ regionId: this.regionId(), includeFulfilled: this.includeFulfilled() }),
    loader: ({ regionId, includeFulfilled }) => this.api.listPassportRequests({ regionId, includeFulfilled }),
  });

  protected readonly members = loadResource<RegionMemberPage, { regionId: string; search: string }>({
    params: () => ({ regionId: this.regionId(), search: this.search().trim() }),
    loader: ({ regionId, search }) =>
      this.regions.listRegionMembers({ regionId, search: search || undefined, verified: true, size: 100 }),
    isEmpty: (page) => page.items.length === 0,
  });

  protected readonly create = runMutation(
    () =>
      this.api.createRegionPassports({
        regionId: this.regionId(),
        createRegionPassports: {
          userIds: [...this.selected()],
          createPdf: true,
          renew: this.renew(),
          validFrom: this.renew() && this.validFrom() ? this.validFrom() : undefined,
          paperSize: this.paperSize(),
          notifyUsers: this.notify(),
        },
      }),
    {
      onSuccess: (job) => {
        this.result.set(undefined);
        this.jobId.set(job.jobId);
      },
    },
  );

  protected toggle(userId: string, on: boolean): void {
    this.selected.update((ids) => {
      const next = new Set(ids);
      if (on) {
        next.add(userId);
      } else {
        next.delete(userId);
      }
      return next;
    });
  }

  protected selectRequests(list: PassportRequestView[]): void {
    this.selected.update((ids) => {
      const next = new Set(ids);
      list.filter((r) => !r.fulfilledAt).forEach((r) => next.add(r.user.id));
      return next;
    });
  }

  protected finished(job: JobProgress): void {
    if (job.status === 'DONE' && job.resultAvailable) {
      this.result.set(job);
    }
    this.requests.reload();
  }

  protected downloadResult(job: JobProgress): void {
    downloadUrl(this.document, `/api/jobs/${job.id}/result`, 'foodsharing-passports.pdf');
  }
}
