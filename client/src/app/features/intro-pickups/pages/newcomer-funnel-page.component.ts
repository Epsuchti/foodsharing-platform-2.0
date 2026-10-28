import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Select } from '@openng/optimus-ui/select';

import { FunnelStep, IntroPickupGuideService, NewcomerFunnel } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/**
 * `/regions/:regionId/newcomers` (EA21): newcomer funnel of a region – steps with counts, median days and stalled
 * people (names only for user managers; guides see counts), and the gap analysis (are there enough EinAb places per
 * category for everybody still in training) with severity colours. Filter: registration window.
 */
@Component({
  selector: 'fs-newcomer-funnel-page',
  imports: [...DATA_STATE_IMPORTS, FormsModule, PageHeaderComponent, RouterLink, Select, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'introPickups.funnel.title' | t" [subtitle]="'introPickups.funnel.subtitle' | t" icon="pi pi-filter" [backLink]="['/regions', regionId()]" [backLabel]="'introPickups.admin.backToRegion' | t" />
    <div class="toolbar">
      <div class="field">
        <label for="funnel-days">{{ 'introPickups.funnel.window' | t }}</label>
        <p-select inputId="funnel-days" [options]="windows()" optionLabel="label" optionValue="value" [ngModel]="days()" (ngModelChange)="days.set($event)" data-testid="introPickups.funnel.window" />
      </div>
      <span class="spacer"></span>
      <a [routerLink]="['/regions', regionId(), 'intro-pickups', 'admin']" class="fs-btn fs-btn--outlined fs-btn--sm" data-testid="introPickups.funnel.admin"><i class="pi pi-shield" aria-hidden="true"></i>{{ 'introPickups.funnel.adminLink' | t }}</a>
    </div>
    <fs-data-state [state]="funnel" skeleton="detail" [askWho]="'introPickups.admin.askWho' | t" data-testid="introPickups.funnel.state">
      <ng-template [fsData]="funnel" let-f>
        <ul class="stats">
          <li class="fs-card stat" data-testid="introPickups.funnel.total"><strong>{{ f.totalUsers }}</strong><span>{{ 'introPickups.funnel.total' | t }}</span></li>
          <li class="fs-card stat"><strong>{{ f.inTrainingUsers }}</strong><span>{{ 'introPickups.funnel.inTraining' | t }}</span></li>
          <li class="fs-card stat" [class.stat--warn]="f.stalledUsers > 0" data-testid="introPickups.funnel.stalled"><strong>{{ f.stalledUsers }}</strong><span>{{ 'introPickups.funnel.stalledUsers' | t }}</span></li>
          <li class="fs-card stat" [class.stat--warn]="f.openSlots < f.introPickupsStillNeeded" data-testid="introPickups.funnel.need">
            <strong>{{ f.openSlots }} / {{ f.introPickupsStillNeeded }}</strong><span>{{ 'introPickups.funnel.slotsVsNeeded' | t }}</span>
          </li>
          @if (f.fairteilerRequired) {
            <li class="fs-card stat" [class.stat--warn]="f.openFairteilerSlots < f.fairteilerVisitsStillNeeded">
              <strong>{{ f.openFairteilerSlots }} / {{ f.fairteilerVisitsStillNeeded }}</strong><span>{{ 'introPickups.funnel.fairteilerVsNeeded' | t }}</span>
            </li>
          }
        </ul>

        <h2 class="section-title"><i class="pi pi-filter" aria-hidden="true"></i>{{ 'introPickups.funnel.steps' | t }}</h2>
        <ol class="list">
          @for (step of f.steps; track step.stage) {
            <li class="fs-card step" [attr.data-stage]="step.stage" data-testid="introPickups.funnel.step">
              <div class="step__head">
                <strong>{{ 'introPickups.funnel.stage.' + step.stage | t }}</strong>
                <span class="tags">
                  <span class="tag" data-testid="introPickups.funnel.count">{{ 'introPickups.funnel.people' | t: { count: step.userCount } }}</span>
                  @if (step.medianDaysInStep !== undefined && step.medianDaysInStep !== null) {
                    <span class="tag">{{ 'introPickups.funnel.median' | t: { days: round(step.medianDaysInStep) } }}</span>
                  }
                  @if (step.stalledCount) {
                    <span class="tag tag--warn" data-testid="introPickups.funnel.stepStalled">{{ 'introPickups.funnel.stalled' | t: { count: step.stalledCount } }}</span>
                  }
                </span>
              </div>
              <div class="bar" aria-hidden="true"><span [style.width.%]="share(step, f)"></span></div>
              @if (step.users.length) {
                <details data-testid="introPickups.funnel.names">
                  <summary>{{ 'introPickups.funnel.showNames' | t }}</summary>
                  <ul class="people">
                    @for (person of step.users; track $index) {
                      <li [class.stalled]="person.stalled" data-testid="introPickups.funnel.person">
                        @if (person.user; as user) {
                          <fs-user-chip [name]="user.displayName" [avatarUrl]="user.avatarUrl" [foodsaverNumber]="user.foodsaverNumber" [subtitle]="'introPickups.funnel.progress' | t: { counted: person.counted, target: person.target, days: person.daysInStage ?? 0 }" />
                        }
                        <span class="muted">{{ 'introPickups.funnel.next.' + person.nextStep | t }}</span>
                      </li>
                    }
                  </ul>
                </details>
              }
            </li>
          }
        </ol>

        <h2 class="section-title"><i class="pi pi-chart-bar" aria-hidden="true"></i>{{ 'introPickups.funnel.gaps' | t }}</h2>
        <p class="muted">{{ 'introPickups.funnel.gapsText' | t: { days: f.gapHorizonDays } }}</p>
        <div class="table-wrap" tabindex="0" role="region" [attr.aria-label]="'introPickups.scrollTable' | t">
          <table class="table" data-testid="introPickups.funnel.gapTable">
            <thead>
              <tr>
                <th>{{ 'introPickups.form.category' | t }}</th>
                <th class="num">{{ 'introPickups.funnel.students' | t }}</th>
                <th class="num">{{ 'introPickups.funnel.openSlots' | t }}</th>
                <th>{{ 'introPickups.funnel.severity' | t }}</th>
              </tr>
            </thead>
            <tbody>
              @for (gap of f.gaps; track gap.category + gap.withFairteiler) {
                <tr [attr.data-severity]="gap.severity" data-testid="introPickups.funnel.gap">
                  <td>{{ 'common.introCategory.' + gap.category | t }}@if (gap.withFairteiler) { · {{ 'introPickups.card.fairteiler' | t }} }</td>
                  <td class="num">{{ gap.students }}</td>
                  <td class="num">{{ gap.openSlots }}</td>
                  <td><span [class]="'tag tag--' + gap.severity">{{ 'introPickups.funnel.gap.' + gap.severity | t }}</span></td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="muted">{{ 'introPickups.funnel.noGaps' | t }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    .toolbar {
      display: flex;
      flex-wrap: wrap;
      align-items: flex-end;
      gap: 0.75rem;
    }
    .spacer {
      flex: 1;
    }
    .stats {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 10rem), 1fr));
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .stat {
      display: grid;
      gap: 0.2rem;
      padding: 0.9rem 1rem;
    }
    .stat strong {
      font-size: 1.6rem;
      font-variant-numeric: tabular-nums;
    }
    .stat span {
      color: var(--fs-text-muted);
      font-size: 0.85rem;
    }
    .stat--warn {
      border-color: var(--fs-warning-border);
      background: var(--fs-warning-soft);
    }
    .step {
      display: grid;
      gap: 0.5rem;
      padding: 0.85rem 1rem;
    }
    .step__head {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .bar {
      height: 0.5rem;
      overflow: hidden;
      border-radius: 999px;
      background: var(--fs-surface-muted);
    }
    .bar span {
      display: block;
      height: 100%;
      border-radius: inherit;
      background: var(--fs-primary);
    }
    details summary {
      cursor: pointer;
      color: var(--fs-primary-strong);
      font-size: 0.9rem;
      font-weight: 600;
    }
    .people {
      display: grid;
      gap: 0.5rem;
      margin: 0.6rem 0 0;
      padding: 0;
      list-style: none;
    }
    .people li {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .people li.stalled {
      padding-left: 0.5rem;
      border-left: 3px solid var(--fs-warning);
    }
  `,
})
export class NewcomerFunnelPageComponent {
  private readonly api = inject(IntroPickupGuideService);
  private readonly i18n = inject(I18nService);

  readonly regionId = input.required<string>();
  /** Registration window in days (0 = everybody). */
  protected readonly days = signal(180);
  protected readonly windows = computed(() =>
    [30, 90, 180, 365, 0].map((value) => ({
      value,
      label: value ? this.i18n.t('introPickups.funnel.lastDays', { days: value }) : this.i18n.t('introPickups.funnel.all'),
    })),
  );

  protected readonly funnel = loadResource<NewcomerFunnel, { regionId: string; days: number }>({
    params: () => ({ regionId: this.regionId(), days: this.days() }),
    loader: ({ regionId, days }) => this.api.getNewcomerFunnel({ regionId, days: days || undefined }),
    isEmpty: () => false,
  });

  protected round(value: number): number {
    return Math.round(value * 10) / 10;
  }

  protected share(step: FunnelStep, funnel: NewcomerFunnel): number {
    return funnel.totalUsers ? Math.round((step.reachedCount / funnel.totalUsers) * 100) : 0;
  }
}
