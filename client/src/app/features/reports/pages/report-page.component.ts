import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { map } from 'rxjs';

import { Report, ReportStatus, ReportsService, WallEntryPage } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { WallComponent, WallPost } from '../../../shared/wall/wall.component';
import { ReportStatusComponent } from '../components/report-item.component';
import {
  CONSEQUENCE_MAX,
  ReportHandlingDraft,
  SETTABLE_STATUSES,
  handlingDraft,
  handlingErrors,
  handlingPatch,
  reasonLabel,
} from '../reports.util';

/**
 * /reports/:reportId (G1; the REPORT_NEW and REPORT_REMINDER bells link here): the report (reported member,
 * reason, store, reporter, message), the handling form (status, consequence, reminder, linked forum thread → one
 * `updateReport` with the changed fields), the handlers' wall and, for ORGA, delete. Report handlers only; the
 * server answers 403 REPORTS_NOT_ACCESSIBLE (forbidden state) or 404 REPORT_NOT_FOUND (not-found state).
 */
@Component({
  selector: 'fs-report-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    InputText,
    PageHeaderComponent,
    ReportStatusComponent,
    RouterLink,
    Select,
    Textarea,
    TPipe,
    UserChipComponent,
    WallComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="report" skeleton="detail" backLink="/dashboard" data-testid="reports.detail.state">
      <ng-template [fsData]="report" let-r>
        <fs-page-header
          [title]="'reports.detail.title' | t: { name: r.reportedUser.displayName }"
          [subtitle]="'reports.detail.created' | t: { date: (r.createdAt | fsDate: 'dateTime') }"
          icon="pi pi-flag"
          [backLink]="['/regions', r.regionId, 'reports']"
          [backLabel]="'reports.detail.back' | t"
        >
          <fs-report-status [status]="r.status" data-testid="reports.detail.status" />
          @if (r.permissions.mayDelete) {
            <p-button
              [label]="'reports.detail.delete' | t"
              icon="pi pi-trash"
              severity="danger"
              [outlined]="true"
              [loading]="remove.busy()"
              (onClick)="confirmDelete()"
              data-testid="reports.detail.delete"
            />
          }
        </fs-page-header>

        <div class="cols">
          <div class="stack">
            <section class="fs-card card" data-testid="reports.detail.content">
              <h2 class="card__title">{{ 'reports.detail.report' | t }}</h2>
              <dl class="facts">
                <dt>{{ 'reports.detail.reported' | t }}</dt>
                <dd>
                  <fs-user-chip
                    [name]="r.reportedUser.displayName"
                    [userId]="r.reportedUser.id"
                    [avatarUrl]="r.reportedUser.avatarUrl"
                    [verified]="r.reportedUser.verified"
                    [sleepMode]="r.reportedUser.sleepMode"
                    [link]="['/profile', r.reportedUser.id]"
                  />
                </dd>
                <dt>{{ 'reports.detail.reason' | t }}</dt>
                <dd data-testid="reports.detail.reason">
                  {{ reason().key | t: reason().params }}
                  @if (r.reasonOther) {<span class="muted"> · {{ r.reasonOther }}</span>}
                </dd>
                @if (r.store) {
                  <dt>{{ 'reports.detail.store' | t }}</dt>
                  <dd><a [routerLink]="['/stores', r.store.id]" data-testid="reports.detail.store">{{ r.store.name }}</a></dd>
                }
                @if (r.reporter) {
                  <dt>{{ 'reports.detail.reporter' | t }}</dt>
                  <dd data-testid="reports.detail.reporter">
                    <fs-user-chip [name]="r.reporter.displayName" [userId]="r.reporter.id" [avatarUrl]="r.reporter.avatarUrl" [link]="['/profile', r.reporter.id]" />
                  </dd>
                }
              </dl>
              <h3 class="sub">{{ 'reports.detail.message' | t }}</h3>
              <p class="message" data-testid="reports.detail.message">{{ r.message }}</p>
            </section>

            <section class="fs-card card" data-testid="reports.detail.wallCard">
              <h2 class="card__title"><i class="pi pi-comments" aria-hidden="true"></i> {{ 'reports.detail.wall' | t }}</h2>
              <p class="muted">{{ 'reports.detail.wallHint' | t }}</p>
              <fs-wall
                [posts]="wallPosts()"
                [state]="wall"
                [mayPost]="r.permissions.mayPost"
                [posting]="postWall.busy()"
                (post)="postWall.run($event)"
                (delete)="deleteWall.run($event)"
                testId="reports.detail.wall"
              />
            </section>
          </div>

          <aside class="stack">
            <section class="fs-card card" data-testid="reports.detail.handling">
              <h2 class="card__title"><i class="pi pi-sliders-h" aria-hidden="true"></i> {{ 'reports.detail.handling' | t }}</h2>
              @if (r.permissions.mayEdit) {
                <form class="form" (ngSubmit)="saveHandling()">
                  <label for="report-status">{{ 'reports.detail.statusLabel' | t }}</label>
                  <p-select
                    inputId="report-status"
                    name="status"
                    [options]="statusOptions()"
                    optionLabel="label"
                    optionValue="value"
                    [ngModel]="draft().status"
                    (ngModelChange)="patchDraft({ status: $event })"
                    appendTo="body"
                    [style]="{ width: '100%' }"
                    data-testid="reports.detail.statusSelect"
                  >
                    <ng-template #item let-option><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template>
                  </p-select>

                  <label for="report-consequence">{{ 'reports.detail.consequence' | t }}</label>
                  <textarea
                    pTextarea
                    id="report-consequence"
                    name="consequence"
                    rows="4"
                    [maxlength]="consequenceMax"
                    [ngModel]="draft().consequence"
                    (ngModelChange)="patchDraft({ consequence: $event })"
                    [attr.aria-invalid]="!!errors().consequence"
                    data-testid="reports.detail.consequence"
                  ></textarea>

                  <label for="report-reminder">{{ 'reports.detail.reminder' | t }}</label>
                  <input
                    pInputText
                    type="datetime-local"
                    id="report-reminder"
                    name="reminderAt"
                    [ngModel]="draft().reminderAt"
                    (ngModelChange)="patchDraft({ reminderAt: $event })"
                    [attr.aria-invalid]="!!errors().reminderAt"
                    aria-describedby="report-reminder-hint"
                    data-testid="reports.detail.reminder"
                  />
                  <small class="hint" id="report-reminder-hint">{{ 'reports.detail.reminderHint' | t }}</small>

                  <label for="report-thread">{{ 'reports.detail.thread' | t }}</label>
                  <input
                    pInputText
                    id="report-thread"
                    name="forumThreadId"
                    [ngModel]="draft().forumThreadId"
                    (ngModelChange)="patchDraft({ forumThreadId: $event })"
                    [attr.aria-invalid]="!!errors().forumThreadId"
                    aria-describedby="report-thread-hint"
                    data-testid="reports.detail.thread"
                  />
                  <small class="hint" id="report-thread-hint" [class.error]="!!errors().forumThreadId">
                    {{ (errors().forumThreadId ?? 'reports.detail.threadHint') | t }}
                  </small>
                  @if (r.forumThreadId) {
                    <a class="thread" [routerLink]="['/forum/threads', r.forumThreadId]" data-testid="reports.detail.threadLink">
                      <i class="pi pi-external-link" aria-hidden="true"></i> {{ 'reports.detail.openThread' | t }}
                    </a>
                  }

                  <p-button
                    type="submit"
                    [label]="'reports.detail.save' | t"
                    icon="pi pi-check"
                    [disabled]="!dirty() || hasErrors()"
                    [loading]="save.busy()"
                    data-testid="reports.detail.save"
                  />
                </form>
              } @else {
                <dl class="facts">
                  <dt>{{ 'reports.detail.consequence' | t }}</dt>
                  <dd data-testid="reports.detail.consequenceText">{{ r.consequence || ('reports.detail.none' | t) }}</dd>
                  <dt>{{ 'reports.detail.reminder' | t }}</dt>
                  <dd>{{ r.reminderAt ? (r.reminderAt | fsDate: 'dateTime') : ('reports.detail.none' | t) }}</dd>
                </dl>
              }
            </section>
          </aside>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .cols { display: grid; gap: 1.25rem; }
    @media (min-width: 60rem) { .cols { grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); align-items: start; } }
    .stack { display: grid; gap: 1.25rem; min-width: 0; }
    .card { display: grid; gap: 0.75rem; min-width: 0; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .sub { margin: 0.25rem 0 0; font-size: 0.95rem; }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .facts { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 0.5rem 1rem; align-items: center; margin: 0; }
    .facts dt { color: var(--fs-text-muted); font-size: 0.85rem; font-weight: 650; }
    .facts dd { margin: 0; overflow-wrap: anywhere; }
    .facts a, .thread { color: var(--fs-primary-strong); }
    .message { margin: 0; padding: 0.75rem; border-left: 3px solid var(--fs-danger); background: var(--fs-surface-muted); white-space: pre-line; overflow-wrap: anywhere; }
    .form { display: grid; gap: 0.4rem; }
    .form label { margin-top: 0.4rem; font-weight: 650; font-size: 0.9rem; }
    .form p-button { justify-self: start; margin-top: 0.6rem; }
    textarea, input { width: 100%; }
    textarea { resize: vertical; }
    .hint { color: var(--fs-text-muted); font-size: 0.82rem; }
    .error { color: var(--fs-danger); font-weight: 600; }
    .thread { display: inline-flex; gap: 0.35rem; align-items: center; font-size: 0.9rem; }
  `,
})
export class ReportPageComponent {
  private readonly api = inject(ReportsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly consequenceMax = CONSEQUENCE_MAX;
  protected readonly reportId = toSignal(this.route.paramMap.pipe(map((params) => params.get('reportId') ?? undefined)));

  protected readonly report = loadResource<Report, string>({
    params: () => this.reportId(),
    loader: (reportId) => this.api.getReport({ reportId }),
    isEmpty: () => false,
  });
  protected readonly reason = computed(() => {
    const r = this.report.data();
    return r ? reasonLabel(r.reason) : { key: '' };
  });
  protected readonly statusOptions = computed(() =>
    SETTABLE_STATUSES.map((value) => ({ value, label: this.i18n.t(`reports.status.${value}`) })),
  );

  // --- handling form: a draft of the loaded report, reset whenever the report (re)loads ---
  protected readonly draft = signal<ReportHandlingDraft>({ status: ReportStatus.ToDo, consequence: '', reminderAt: '', forumThreadId: '' });
  protected readonly errors = computed(() => handlingErrors(this.draft()));
  protected readonly hasErrors = computed(() => Object.keys(this.errors()).length > 0);
  protected readonly dirty = computed(() => {
    const r = this.report.data();
    return !!r && Object.keys(handlingPatch(r, this.draft())).length > 0;
  });

  protected readonly save = runMutation(
    () => this.api.updateReport({ reportId: this.reportId()!, reportUpdate: handlingPatch(this.report.data()!, this.draft()) }),
    { successToastKey: 'reports.toast.saved', onSuccess: () => this.report.reload() },
  );

  // --- wall (handlers write, ORGA or the author delete) ---
  /** The wall loads with the report's id, not on every report reload. */
  private readonly loadedId = computed(() => this.report.data()?.id);
  protected readonly wall = loadResource<WallEntryPage, string>({
    params: () => this.loadedId(),
    loader: (reportId) => this.api.listReportWall({ reportId, size: 50 }),
  });
  protected readonly wallPosts = computed<WallPost[]>(() => this.wall.data()?.items ?? []);
  protected readonly postWall = runMutation(
    (body: string) => this.api.createReportWallPost({ reportId: this.reportId()!, wallEntryCreate: { body } }),
    { onSuccess: () => this.wall.reload() },
  );
  protected readonly deleteWall = runMutation(
    (post: WallPost) => this.api.deleteReportWallPost({ reportId: this.reportId()!, postId: post.id }),
    { successToastKey: 'reports.toast.wallDeleted', onSuccess: () => this.wall.reload() },
  );

  // --- delete (ORGA) ---
  protected readonly remove = runMutation(() => this.api.deleteReport({ reportId: this.reportId()! }), {
    successToastKey: 'reports.toast.deleted',
    onSuccess: () => {
      const regionId = this.report.data()?.regionId;
      void this.router.navigate(regionId ? ['/regions', regionId, 'reports'] : ['/dashboard']);
    },
  });

  constructor() {
    effect(() => {
      const r = this.report.data();
      if (r) untracked(() => this.draft.set(handlingDraft(r)));
    });
  }

  protected patchDraft(change: Partial<ReportHandlingDraft>): void {
    this.draft.update((draft) => ({ ...draft, ...change }));
  }

  protected saveHandling(): void {
    if (this.dirty() && !this.hasErrors()) void this.save.run();
  }

  protected confirmDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('reports.detail.deleteTitle'),
      message: this.i18n.t('reports.detail.deleteText'),
      acceptLabel: this.i18n.t('reports.detail.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }
}
