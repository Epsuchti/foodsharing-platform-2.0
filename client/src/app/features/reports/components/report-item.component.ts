import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ReportStatus, ReportSummary } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { reasonLabel } from '../reports.util';

/** Status pill of a report (`reports.status.<STATUS>`), coloured by how much work is left. */
@Component({
  selector: 'fs-report-status',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-report-status', '[attr.data-status]': 'status()', '[attr.data-tone]': 'tone()' },
  template: `{{ 'reports.status.' + status() | t }}`,
  styles: `
    :host {
      display: inline-block;
      padding: 0.1rem 0.6rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      color: var(--fs-text);
      font-size: 0.8rem;
      font-weight: 650;
      white-space: nowrap;
    }
    :host([data-tone='open']) { background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    :host([data-tone='done']) { background: var(--fs-primary-soft); }
    :host([data-tone='deleted']) { background: var(--fs-danger-soft); }
  `,
})
export class ReportStatusComponent {
  readonly status = input.required<ReportStatus>();
  protected readonly tone = computed(() => {
    switch (this.status()) {
      case ReportStatus.ToDo:
        return 'open';
      case ReportStatus.Completed:
        return 'done';
      case ReportStatus.Deleted:
        return 'deleted';
      default:
        return 'busy';
    }
  });
}

/**
 * One report in a list (region reports tab, reports block of a profile): reported member, status, reason, store,
 * reporter, dates and the link to `/reports/:id`. `showReported = false` on a profile, where the member is known.
 */
@Component({
  selector: 'fs-report-item',
  imports: [DateTimePipe, ReportStatusComponent, RouterLink, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-card',
    'data-testid': 'reports.item',
    '[attr.data-report-id]': 'report().id',
    '[attr.data-status]': 'report().status',
  },
  template: `
    @let r = report();
    <div class="head">
      @if (showReported()) {
        <fs-user-chip
          [name]="r.reportedUser.displayName"
          [userId]="r.reportedUser.id"
          [avatarUrl]="r.reportedUser.avatarUrl"
          [verified]="r.reportedUser.verified"
          [sleepMode]="r.reportedUser.sleepMode"
          [link]="['/profile', r.reportedUser.id]"
        />
      } @else {
        <span class="date">{{ r.createdAt | fsDate: 'date' }}</span>
      }
      <fs-report-status [status]="r.status" data-testid="reports.item.status" />
    </div>
    <p class="reason" data-testid="reports.item.reason">
      <strong>{{ reason().key | t: reason().params }}</strong>
      @if (r.reasonOther) {
        <span class="muted"> · {{ r.reasonOther }}</span>
      }
    </p>
    <p class="meta">
      @if (showReported()) {
        <span><i class="pi pi-calendar" aria-hidden="true"></i> {{ r.createdAt | fsDate: 'date' }}</span>
      }
      @if (r.store) {
        <span data-testid="reports.item.store"><i class="pi pi-shop" aria-hidden="true"></i> {{ r.store.name }}</span>
      }
      @if (r.reporter) {
        <span data-testid="reports.item.reporter"><i class="pi pi-user" aria-hidden="true"></i> {{ 'reports.item.by' | t: { name: r.reporter.displayName } }}</span>
      }
      @if (r.reminderAt) {
        <span data-testid="reports.item.reminder"><i class="pi pi-bell" aria-hidden="true"></i> {{ 'reports.item.reminder' | t: { date: (r.reminderAt | fsDate: 'dateTime') } }}</span>
      }
    </p>
    <a class="open" [routerLink]="['/reports', r.id]" data-testid="reports.item.open">
      {{ 'reports.item.open' | t }} <i class="pi pi-arrow-right" aria-hidden="true"></i>
    </a>
  `,
  styles: `
    :host { display: grid; gap: 0.45rem; min-width: 0; }
    .head { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; justify-content: space-between; }
    .date { font-weight: 650; }
    .reason { margin: 0; overflow-wrap: anywhere; }
    .muted { color: var(--fs-text-muted); }
    .meta { display: flex; flex-wrap: wrap; gap: 0.25rem 1rem; margin: 0; color: var(--fs-text-muted); font-size: 0.85rem; }
    .open { justify-self: start; display: inline-flex; gap: 0.35rem; align-items: center; color: var(--fs-primary-strong); font-weight: 650; text-decoration: none; }
    .open:hover, .open:focus-visible { text-decoration: underline; }
  `,
})
export class ReportItemComponent {
  readonly report = input.required<ReportSummary>();
  readonly showReported = input(true);
  protected readonly reason = computed(() => reasonLabel(this.report().reason));
}
