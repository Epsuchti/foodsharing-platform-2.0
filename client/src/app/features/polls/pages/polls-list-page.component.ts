import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';

import { PollSummary, PollsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { byStatus } from '../polls.resource';

/**
 * /regions/:regionId/polls and /polls/mine (V1): running / upcoming / ended / cancelled sections with status chip,
 * vote count and "voted" check. "New poll" is offered to members; the server checks the VOTING group (A36).
 */
@Component({
  selector: 'fs-polls-list-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(mine() ? 'polls.mine.title' : 'polls.title') | t"
      [subtitle]="regionName()"
      icon="pi pi-chart-bar"
      [backLink]="regionId() ? ['/regions', regionId()] : undefined"
      [backLabel]="regionName()"
    >
      @if (regionId() && member()) {
        <p-button [label]="'polls.new' | t" icon="pi pi-plus" [routerLink]="['/polls/new']" [queryParams]="{ regionId: regionId() }" data-testid="polls.list.new" />
      }
    </fs-page-header>
    <fs-data-state [state]="polls" skeleton="list" [emptyTitle]="'polls.emptyTitle' | t" [emptyText]="(mine() ? 'polls.mine.emptyText' : 'polls.emptyText') | t" emptyIcon="pi pi-chart-bar" data-testid="polls.list.state">
      <ng-template [fsData]="polls" let-list>
        @for (group of groups(list); track group.status) {
          <section class="group" [attr.data-status]="group.status" data-testid="polls.list.group">
            <h2>{{ 'polls.section.' + group.status | t }}</h2>
            <ul class="items fs-card">
              @for (p of group.polls; track p.id) {
                <li>
                  <a class="item" [routerLink]="['/polls', p.id]" data-testid="polls.list.item" [attr.data-poll-id]="p.id" [attr.data-status]="p.status">
                    <span class="item__text">
                      <span class="item__name">{{ p.name }}</span>
                      <span class="item__meta">
                        {{ 'polls.type.' + p.type | t }} ·
                        @if (p.status === 'UPCOMING') {
                          {{ 'polls.startsAt' | t: { date: (p.startsAt | fsDate: 'dateTime') } }}
                        } @else {
                          {{ 'polls.endsAt' | t: { date: (p.endsAt | fsDate: 'dateTime') } }}
                        }
                      </span>
                    </span>
                    <span class="item__side">
                      <span class="chip chip--{{ p.status }}">{{ 'polls.status.' + p.status | t }}</span>
                      <span class="muted">{{ 'polls.votes' | t: { count: p.voteCount } }}</span>
                      @if (p.voted) {<span class="voted" data-testid="polls.list.voted"><i class="pi pi-check" aria-hidden="true"></i> {{ 'polls.voted' | t }}</span>}
                    </span>
                  </a>
                </li>
              }
            </ul>
          </section>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }
    .group { display: grid; gap: 0.4rem; }
    h2 { margin: 0; font-size: 1rem; color: var(--fs-text-muted); }
    .items { list-style: none; margin: 0; padding: 0; }
    .items li + li { border-top: 1px solid var(--fs-border); }
    .item { display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem 1rem; color: inherit; text-decoration: none; }
    .item:hover, .item:focus-visible { background: var(--fs-surface-muted); }
    .item__text { display: grid; gap: 0.15rem; flex: 1; min-width: 0; }
    .item__name { font-weight: 600; }
    .item__meta, .muted { color: var(--fs-text-muted); font-size: 0.875rem; }
    .item__side { display: grid; justify-items: end; gap: 0.2rem; }
    .chip { padding: 0.05rem 0.5rem; border-radius: 999px; border: 1px solid var(--fs-border); font-size: 0.8rem; font-weight: 600; color: var(--fs-text); }
    .chip--RUNNING { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .chip--UPCOMING { border-color: var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .voted { color: var(--fs-primary-strong); font-size: 0.85rem; font-weight: 600; }
  `,
})
export class PollsListPageComponent {
  private readonly api = inject(PollsService);
  private readonly session = inject(SessionService);

  readonly regionId = input<string | undefined>(undefined);
  readonly mine = input(false, { transform: (value: unknown) => value === true });
  private readonly unit = computed(() => (this.session.session().units ?? []).find((u) => u.id === this.regionId()));
  protected readonly regionName = computed(() => this.unit()?.name);
  protected readonly member = computed(() => !!this.unit()?.member);

  protected readonly polls = loadResource<PollSummary[], string>({
    params: () => this.regionId() ?? '',
    loader: (regionId) => (regionId ? this.api.listRegionPolls({ regionId }) : this.api.listMyOpenPolls()),
  });

  protected groups(list: PollSummary[]) {
    return byStatus(list);
  }
}
