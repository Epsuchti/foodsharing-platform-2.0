import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';

import { MediationInfo, ReportsService, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { UserPickerComponent } from '../../../shared/user-picker/user-picker.component';
import { RegionPageContext } from '../../regions/region-page.context';
import { MEDIATION_MESSAGE_MAX, MEDIATION_MESSAGE_MIN } from '../reports.util';

/**
 * /regions/:regionId/mediation (G2, `getMediationInfo`, `requestMediation`): the mediation team of the region (its
 * MEDIATION group or the nearest ancestor's, else the ambassadors) with the group's mail address, and a request
 * form (message ≥ 10, optional other person involved). The team gets a MEDIATION_REQUEST bell and a mail. When
 * the region has switched mediation off, the page says so and offers no form.
 */
@Component({
  selector: 'fs-region-mediation-tab',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, Textarea, TPipe, UserChipComponent, UserPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="info" skeleton="detail" data-testid="reports.mediation.state">
      <ng-template [fsData]="info" let-m>
        <div class="cols">
          <section class="fs-card card" data-testid="reports.mediation.team">
            <h2 class="card__title"><i class="pi pi-users" aria-hidden="true"></i> {{ 'reports.mediation.team' | t }}</h2>
            <p class="muted">{{ 'reports.mediation.intro' | t }}</p>
            @if (m.team.length) {
              <ul class="team">
                @for (member of m.team; track member.id) {
                  <li data-testid="reports.mediation.member" [attr.data-user-id]="member.id">
                    <fs-user-chip
                      [name]="member.displayName"
                      [userId]="member.id"
                      [avatarUrl]="member.avatarUrl"
                      [verified]="member.verified"
                      [sleepMode]="member.sleepMode"
                      [link]="['/profile', member.id]"
                    />
                  </li>
                }
              </ul>
            } @else {
              <p class="muted" data-testid="reports.mediation.noTeam">{{ 'reports.mediation.noTeam' | t }}</p>
            }
            @if (m.mailboxAddress) {
              <p class="mail">
                <i class="pi pi-envelope" aria-hidden="true"></i>
                <a [href]="'mailto:' + m.mailboxAddress" data-testid="reports.mediation.mail">{{ m.mailboxAddress }}</a>
              </p>
            }
          </section>

          <section class="fs-card card" data-testid="reports.mediation.request">
            <h2 class="card__title"><i class="pi pi-comments" aria-hidden="true"></i> {{ 'reports.mediation.requestTitle' | t }}</h2>
            @if (!m.enabled) {
              <p class="notice" data-testid="reports.mediation.disabled">
                <i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'reports.mediation.disabled' | t }}
              </p>
            } @else if (sent()) {
              <p class="notice notice--ok" role="status" data-testid="reports.mediation.sent">
                <i class="pi pi-check-circle" aria-hidden="true"></i> {{ 'reports.mediation.sent' | t }}
              </p>
              <p-button
                [label]="'reports.mediation.another' | t"
                severity="secondary"
                [outlined]="true"
                (onClick)="sent.set(false)"
                data-testid="reports.mediation.another"
              />
            } @else {
              <form class="form" (ngSubmit)="submit()">
                <p class="muted">{{ 'reports.mediation.requestText' | t }}</p>
                @if (counterpart(); as person) {
                  <div class="picked" data-testid="reports.mediation.counterpart" [attr.data-user-id]="person.id">
                    <span class="label">{{ 'reports.mediation.counterpart' | t }}</span>
                    <fs-user-chip [name]="person.displayName" [userId]="person.id" [avatarUrl]="person.avatarUrl" />
                    <p-button
                      icon="pi pi-times"
                      severity="secondary"
                      [text]="true"
                      [rounded]="true"
                      [ariaLabel]="'reports.mediation.removeCounterpart' | t"
                      (onClick)="counterpart.set(null)"
                      data-testid="reports.mediation.counterpart.remove"
                    />
                  </div>
                } @else {
                  <fs-user-picker
                    [label]="'reports.mediation.counterpartOptional' | t"
                    [excludeIds]="teamIds()"
                    (picked)="counterpart.set($event)"
                    testId="reports.mediation.picker"
                  />
                }
                <label class="label" for="mediation-message">{{ 'reports.mediation.message' | t }}</label>
                <textarea
                  pTextarea
                  id="mediation-message"
                  name="message"
                  rows="6"
                  [maxlength]="messageMax"
                  [ngModel]="message()"
                  (ngModelChange)="message.set($event)"
                  [attr.aria-invalid]="showError()"
                  aria-describedby="mediation-message-hint"
                  data-testid="reports.mediation.message"
                ></textarea>
                <small id="mediation-message-hint" class="hint" [class.error]="showError()">
                  @if (showError()) {
                    {{ 'reports.mediation.tooShort' | t: { min: messageMin } }}
                  } @else {
                    {{ 'reports.mediation.count' | t: { count: length(), min: messageMin } }}
                  }
                </small>
                <p-button
                  type="submit"
                  [label]="'reports.mediation.send' | t"
                  icon="pi pi-send"
                  [loading]="send.busy()"
                  data-testid="reports.mediation.send"
                />
              </form>
            }
          </section>
        </div>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; }
    .cols { display: grid; gap: 1.25rem; }
    @media (min-width: 60rem) { .cols { grid-template-columns: minmax(0, 1fr) minmax(0, 1.3fr); align-items: start; } }
    .card { display: grid; gap: 0.75rem; min-width: 0; }
    .card__title { display: flex; gap: 0.5rem; align-items: center; margin: 0; font-size: 1.05rem; }
    .card__title .pi { color: var(--fs-primary-strong); }
    .muted { margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .team { display: grid; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
    .mail { display: flex; gap: 0.5rem; align-items: center; margin: 0; overflow-wrap: anywhere; }
    .mail a { color: var(--fs-primary-strong); }
    .form { display: grid; gap: 0.6rem; }
    .label { font-weight: 650; font-size: 0.9rem; }
    .picked { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
    textarea { width: 100%; resize: vertical; }
    .hint { color: var(--fs-text-muted); font-size: 0.82rem; }
    .error { color: var(--fs-danger); font-weight: 600; }
    .form p-button { justify-self: start; }
    .notice {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      margin: 0;
      padding: 0.6rem 0.8rem;
      border: 1px solid var(--fs-warning-border);
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .notice--ok { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-text); }
  `,
})
export class RegionMediationTab {
  private readonly ctx = inject(RegionPageContext);
  private readonly api = inject(ReportsService);

  protected readonly messageMin = MEDIATION_MESSAGE_MIN;
  protected readonly messageMax = MEDIATION_MESSAGE_MAX;

  protected readonly info = loadResource<MediationInfo, string>({
    params: () => this.ctx.regionId() ?? undefined,
    loader: (regionId) => this.api.getMediationInfo({ regionId }),
    isEmpty: () => false,
  });

  protected readonly message = signal('');
  protected readonly counterpart = signal<UserSummary | null>(null);
  protected readonly sent = signal(false);
  private readonly attempted = signal(false);
  protected readonly length = computed(() => this.message().trim().length);
  protected readonly showError = computed(() => this.attempted() && this.length() < MEDIATION_MESSAGE_MIN);
  /** The team members are not offered as the other person. */
  protected readonly teamIds = computed(() => (this.info.data()?.team ?? []).map((member) => member.id));

  protected readonly send = runMutation(
    () =>
      this.api.requestMediation({
        regionId: this.ctx.regionId()!,
        mediationRequestCreate: { message: this.message().trim(), counterpartUserId: this.counterpart()?.id },
      }),
    {
      successToastKey: 'reports.toast.mediationSent',
      onSuccess: () => {
        this.sent.set(true);
        this.message.set('');
        this.counterpart.set(null);
        this.attempted.set(false);
      },
    },
  );

  protected submit(): void {
    this.attempted.set(true);
    if (this.length() >= MEDIATION_MESSAGE_MIN) void this.send.run();
  }
}
