import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { filter } from 'rxjs';

import { Poll, PollOption, PollType, PollsService, RealtimeEventType } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { POLL_VALUES, buildVote, optionTotals, seededShuffle } from '../polls.resource';

/**
 * /polls/:pollId (V1): description, dates, eligible and vote counts, the ballot of the poll type (options shuffled
 * per user when `shuffleOptions`), a confirm before the irrevocable vote, results once ended, edit before the start
 * and cancel for author / ORGA.
 */
@Component({
  selector: 'fs-poll-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, MarkdownComponent, PageHeaderComponent, RouterLink, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="poll" skeleton="detail" data-testid="polls.poll.state">
      <ng-template [fsData]="poll" let-p>
        <fs-page-header [title]="p.name" icon="pi pi-chart-bar" [backLink]="['/regions', p.regionId, 'polls']" [backLabel]="'polls.title' | t">
          @if (p.mayEdit) {
            <p-button icon="pi pi-pencil" [label]="'polls.edit' | t" severity="secondary" [outlined]="true" [routerLink]="['/polls', p.id, 'edit']" data-testid="polls.poll.edit" />
          }
          @if (p.mayCancel) {
            <p-button icon="pi pi-ban" [label]="'polls.cancel' | t" severity="danger" [outlined]="true" (onClick)="confirmCancel(p)" data-testid="polls.poll.cancel" />
          }
        </fs-page-header>

        <section class="fs-card block facts" data-testid="polls.poll.facts" [attr.data-status]="p.status">
          <p><span class="chip chip--{{ p.status }}" data-testid="polls.poll.status">{{ 'polls.status.' + p.status | t }}</span> {{ 'polls.type.' + p.type | t }} · {{ 'polls.scope.' + p.scope | t }}</p>
          <p><i class="pi pi-clock" aria-hidden="true"></i> {{ p.startsAt | fsDate: 'dateTime' }} – {{ p.endsAt | fsDate: 'dateTime' }}</p>
          <p data-testid="polls.poll.counts">
            {{ 'polls.votes' | t: { count: p.voteCount } }}
            @if (p.eligibleCount !== undefined && p.eligibleCount !== null) { · {{ 'polls.eligible' | t: { count: p.eligibleCount } }} }
          </p>
          @if (p.cancelledAt) {
            <p class="muted" data-testid="polls.poll.cancelled">{{ 'polls.cancelledBy' | t: { name: p.cancelledBy?.displayName ?? '', date: (p.cancelledAt | fsDate: 'dateTime') } }}</p>
          }
          @if (p.description) {<fs-markdown [markdown]="p.description" />}
        </section>

        @if (p.status === 'ENDED') {
          <section class="fs-card block" data-testid="polls.poll.results">
            <h2>{{ 'polls.results' | t }}</h2>
            @for (o of p.options; track o.index) {
              <div class="result" data-testid="polls.poll.result" [attr.data-index]="o.index">
                <div class="result__head"><span>{{ o.text }}</span><strong>{{ resultLabel(p, o) }}</strong></div>
                @if (p.type === 'SELECT_ONE' || p.type === 'SELECT_MULTIPLE') {
                  <div class="bar" role="img" [attr.aria-label]="o.text + ': ' + resultLabel(p, o)"><span [style.width.%]="share(p, o)"></span></div>
                } @else {
                  <ul class="values">
                    @for (v of o.values ?? []; track v.value) {<li>{{ valueLabel(p.type, v.value) }}: {{ v.votes }}</li>}
                  </ul>
                }
              </div>
            }
          </section>
        } @else if (p.voted) {
          <p class="fs-card block voted" data-testid="polls.poll.voted"><i class="pi pi-check-circle" aria-hidden="true"></i> {{ 'polls.votedNote' | t }}</p>
        } @else if (p.mayVote) {
          <form class="fs-card block ballot" (submit)="$event.preventDefault(); confirmVote(p)" data-testid="polls.poll.ballot" [attr.data-type]="p.type">
            <h2 id="ballot-title">{{ 'polls.ballot.' + p.type | t }}</h2>
            @for (o of ordered(p); track o.index) {
              @switch (p.type) {
                @case ('SELECT_ONE') {
                  <label class="choice"><input type="radio" name="choice" [checked]="choice()[o.index] === 1" (change)="setOnly(o.index)" data-testid="polls.ballot.option" [attr.data-index]="o.index" /> {{ o.text }}</label>
                }
                @case ('SELECT_MULTIPLE') {
                  <label class="choice"><input type="checkbox" [checked]="choice()[o.index] === 1" (change)="toggle(o.index)" data-testid="polls.ballot.option" [attr.data-index]="o.index" /> {{ o.text }}</label>
                }
                @default {
                  <fieldset class="scale" data-testid="polls.ballot.option" [attr.data-index]="o.index">
                    <legend>{{ o.text }}</legend>
                    @for (v of values(p.type); track v) {
                      <label class="scale__value">
                        <input type="radio" [name]="'option-' + o.index" [checked]="choice()[o.index] === v" (change)="set(o.index, v)" [attr.data-value]="v" />
                        <span>{{ valueLabel(p.type, v) }}</span>
                      </label>
                    }
                  </fieldset>
                }
              }
            }
            <div class="row">
              <p-button type="submit" icon="pi pi-send" [label]="'polls.ballot.submit' | t" [disabled]="!vote(p)" [loading]="cast.busy()" data-testid="polls.ballot.submit" />
            </div>
          </form>
        } @else if (p.status === 'UPCOMING') {
          <p class="fs-card block muted" data-testid="polls.poll.upcoming">{{ 'polls.upcomingNote' | t: { date: (p.startsAt | fsDate: 'dateTime') } }}</p>
        } @else if (p.status === 'RUNNING') {
          <p class="fs-card block muted" data-testid="polls.poll.notEligible">{{ 'polls.notEligible' | t }}</p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 48rem); margin: 0 auto; }
    .block { display: grid; gap: 0.5rem; padding: 1rem; margin: 0; }
    .facts p { margin: 0; display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; }
    h2 { margin: 0; font-size: 1.1rem; }
    .muted { color: var(--fs-text-muted); }
    .voted { color: var(--fs-primary-strong); font-weight: 600; }
    .chip { padding: 0.05rem 0.5rem; border-radius: 999px; border: 1px solid var(--fs-border); font-size: 0.8rem; font-weight: 600; color: var(--fs-text); }
    .chip--RUNNING { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .chip--UPCOMING { border-color: var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .choice { display: flex; align-items: center; gap: 0.6rem; min-height: 2.75rem; padding: 0 0.5rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); cursor: pointer; }
    .choice input, .scale input { width: 1.25rem; height: 1.25rem; accent-color: var(--fs-primary); }
    .scale { display: flex; flex-wrap: wrap; gap: 0.4rem; margin: 0; padding: 0.5rem; border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); }
    .scale legend { padding: 0 0.25rem; font-weight: 600; }
    .scale__value { display: inline-flex; align-items: center; gap: 0.3rem; min-height: 2.5rem; min-width: 3rem; padding: 0 0.4rem; cursor: pointer; }
    .row { display: flex; justify-content: flex-end; }
    .result { display: grid; gap: 0.25rem; }
    .result__head { display: flex; justify-content: space-between; gap: 0.5rem; }
    .bar { height: 0.6rem; border-radius: 999px; background: var(--fs-surface-muted); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--fs-primary); }
    .values { display: flex; flex-wrap: wrap; gap: 0.75rem; margin: 0; padding: 0; list-style: none; color: var(--fs-text-muted); font-size: 0.875rem; }
  `,
})
export class PollPageComponent {
  private readonly api = inject(PollsService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly pollId = input.required<string>();
  protected readonly choice = signal<Record<number, number | undefined>>({});

  protected readonly poll = loadResource<Poll, string>({
    params: () => this.pollId(),
    loader: (pollId) => this.api.getPoll({ pollId }),
    isEmpty: () => false,
  });

  private readonly me = computed(() => this.session.user()?.id ?? '');

  constructor() {
    const realtime = inject(RealtimeService);
    realtime
      .on(RealtimeEventType.PollState)
      .pipe(
        filter((event) => (event.payload as { pollId?: string } | undefined)?.pollId === this.pollId()),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.poll.refresh());
    const unregister = realtime.onReconnect(() => this.poll.refresh());
    inject(DestroyRef).onDestroy(unregister);
  }

  protected ordered(p: Poll): PollOption[] {
    return p.shuffleOptions ? seededShuffle(p.options, `${this.me()}:${p.id}`) : p.options;
  }

  protected values(type: PollType): readonly number[] {
    return POLL_VALUES[type];
  }

  protected valueLabel(type: PollType, value: number): string {
    if (type === PollType.Thumbs) return this.i18n.t(`polls.thumbs.${value === 1 ? 'up' : value === 0 ? 'neutral' : 'down'}`);
    return value > 0 ? `+${value}` : String(value);
  }

  protected setOnly(index: number): void {
    this.choice.set({ [index]: 1 });
  }

  protected toggle(index: number): void {
    this.choice.update((c) => ({ ...c, [index]: c[index] === 1 ? undefined : 1 }));
  }

  protected set(index: number, value: number): void {
    this.choice.update((c) => ({ ...c, [index]: value }));
  }

  protected vote(p: Poll): Record<string, number> | null {
    return buildVote(p.type, p.options, this.choice());
  }

  protected share(p: Poll, o: PollOption): number {
    const max = Math.max(1, p.voteCount);
    return Math.round((optionTotals(o).total / max) * 100);
  }

  protected resultLabel(p: Poll, o: PollOption): string {
    const { total, sum } = optionTotals(o);
    return p.type === PollType.SelectOne || p.type === PollType.SelectMultiple
      ? this.i18n.t('polls.votes', { count: total })
      : this.i18n.t('polls.sum', { sum: sum > 0 ? `+${sum}` : String(sum) });
  }

  protected readonly cast = runMutation(
    (values: Record<string, number>) => this.api.votePoll({ pollId: this.pollId(), pollVote: { values } }),
    {
      successToastKey: 'polls.ballot.saved',
      onSuccess: () => {
        this.choice.set({});
        this.poll.reload();
      },
      onError: () => this.poll.reload(),
    },
  );

  protected readonly cancelPoll = runMutation(() => this.api.cancelPoll({ pollId: this.pollId() }), {
    successToastKey: 'polls.cancelled',
    onSuccess: () => this.poll.reload(),
  });

  protected confirmVote(p: Poll): void {
    const values = this.vote(p);
    if (!values) return;
    this.confirm.confirm({
      header: this.i18n.t('polls.ballot.confirmTitle'),
      message: this.i18n.t('polls.ballot.confirmText'),
      acceptLabel: this.i18n.t('polls.ballot.submit'),
      rejectLabel: this.i18n.t('common.cancel'),
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.cast.run(values),
    });
  }

  protected confirmCancel(p: Poll): void {
    this.confirm.confirm({
      header: this.i18n.t('polls.cancelTitle'),
      message: this.i18n.t('polls.cancelText', { name: p.name }),
      acceptLabel: this.i18n.t('polls.cancel'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.cancelPoll.run(),
    });
  }
}
