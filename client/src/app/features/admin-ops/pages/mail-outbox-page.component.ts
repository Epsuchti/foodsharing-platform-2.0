import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { SelectButton } from '@openng/optimus-ui/selectbutton';

import { AdminMailService, MailOutboxItem, MailOutboxPage, MailOutboxStatus } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

export const OUTBOX_PAGE_SIZE = 25;

/**
 * `/admin/mail-outbox` (N6, ORGA): outgoing mails newest first, filter by status, search recipient/subject,
 * error details and "Retry" for failed mails, paging.
 */
@Component({
  selector: 'fs-mail-outbox-page',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, InputText, PageHeaderComponent, SelectButton, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'adminOps.outbox.title' | t" [subtitle]="'adminOps.outbox.subtitle' | t" icon="pi pi-envelope" />
    <div class="filters">
      <div class="field">
        <span id="outbox-status">{{ 'adminOps.outbox.status' | t }}</span>
        <p-selectbutton
          [options]="statusOptions()"
          optionLabel="label"
          optionValue="value"
          [allowEmpty]="false"
          [ngModel]="status()"
          (ngModelChange)="setStatus($event)"
          ariaLabelledBy="outbox-status"
          data-testid="adminOps.outbox.filter"
        />
      </div>
      <div class="field">
        <label for="outbox-search">{{ 'adminOps.outbox.search' | t }}</label>
        <input pInputText id="outbox-search" type="search" [ngModel]="search()" (ngModelChange)="setSearch($event)" data-testid="adminOps.outbox.search" />
      </div>
    </div>
    <fs-data-state [state]="outbox" skeleton="table" [skeletonRows]="8" emptyIcon="pi pi-inbox" [emptyTitle]="'adminOps.outbox.empty' | t" data-testid="adminOps.outbox.state">
      <ng-template [fsData]="outbox" let-result>
        <div class="table-wrap fs-card">
          <table class="table" data-testid="adminOps.outbox.table">
            <thead>
              <tr>
                <th scope="col">{{ 'adminOps.outbox.created' | t }}</th>
                <th scope="col">{{ 'adminOps.outbox.to' | t }}</th>
                <th scope="col">{{ 'adminOps.outbox.subject' | t }}</th>
                <th scope="col">{{ 'adminOps.outbox.status' | t }}</th>
                <th scope="col"><span class="fs-visually-hidden">{{ 'adminOps.jobs.actions' | t }}</span></th>
              </tr>
            </thead>
            <tbody>
              @for (mail of result.items; track mail.id) {
                <tr data-testid="adminOps.outbox.row" [attr.data-status]="mail.status" [attr.data-id]="mail.id">
                  <td>{{ mail.createdAt | fsDate: 'dateTime' }}</td>
                  <td>{{ mail.to }}</td>
                  <td>
                    {{ mail.subject }}
                    @if (mail.template) {
                      <code class="muted">{{ mail.template }}</code>
                    }
                    @if (mail.lastError) {
                      <details class="error">
                        <summary>{{ 'adminOps.outbox.error' | t: { attempts: mail.attempts } }}</summary>
                        <pre data-testid="adminOps.outbox.lastError">{{ mail.lastError }}</pre>
                      </details>
                    }
                  </td>
                  <td>
                    <span [class]="'badge badge--' + mail.status" data-testid="adminOps.outbox.statusBadge">{{ 'adminOps.outbox.status.' + mail.status | t }}</span>
                    @if (mail.sentAt) {
                      <span class="muted">{{ mail.sentAt | fsDate: 'dateTime' }}</span>
                    } @else if (mail.nextAttemptAt) {
                      <span class="muted">{{ 'adminOps.outbox.next' | t: { time: (mail.nextAttemptAt | fsDate: 'dateTime') } }}</span>
                    }
                  </td>
                  <td class="num">
                    @if (mail.status === 'FAILED') {
                      <p-button
                        [label]="'adminOps.outbox.retry' | t"
                        icon="pi pi-replay"
                        size="small"
                        [outlined]="true"
                        [loading]="retry.busy()"
                        (onClick)="retry.run(mail)"
                        data-testid="adminOps.outbox.retry"
                      />
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (result.page.totalPages > 1) {
          <div class="pager">
            <p-button icon="pi pi-angle-left" [text]="true" [disabled]="page() === 0" (onClick)="page.set(page() - 1)" [ariaLabel]="'adminOps.outbox.previous' | t" data-testid="adminOps.outbox.prev" />
            <span>{{ 'adminOps.outbox.pageOf' | t: { page: page() + 1, pages: result.page.totalPages } }}</span>
            <p-button icon="pi pi-angle-right" [text]="true" [disabled]="page() + 1 >= result.page.totalPages" (onClick)="page.set(page() + 1)" [ariaLabel]="'adminOps.outbox.nextPage' | t" data-testid="adminOps.outbox.next" />
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styleUrl: '../admin-ops.scss',
})
export class MailOutboxPageComponent {
  private readonly api = inject(AdminMailService);
  private readonly i18n = inject(I18nService);
  protected readonly status = signal<MailOutboxStatus | 'ALL'>('ALL');
  protected readonly search = signal('');
  protected readonly page = signal(0);

  protected readonly statusOptions = computed(() => {
    this.i18n.language();
    return (['ALL', ...Object.values(MailOutboxStatus)] as const).map((value) => ({
      value,
      label: this.i18n.t(`adminOps.outbox.status.${value}`),
    }));
  });

  protected readonly outbox = loadResource<MailOutboxPage, { status: MailOutboxStatus | 'ALL'; search: string; page: number }>({
    params: () => ({ status: this.status(), search: this.search().trim(), page: this.page() }),
    loader: ({ status, search, page }) =>
      this.api.listMailOutbox({ status: status === 'ALL' ? undefined : status, search: search || undefined, page, size: OUTBOX_PAGE_SIZE }),
    isEmpty: (result) => result.items.length === 0,
  });

  protected readonly retry = runMutation((mail: MailOutboxItem) => this.api.retryMail({ mailId: mail.id }), {
    successToastKey: 'adminOps.outbox.retried',
    onSuccess: () => this.outbox.reload(),
  });

  protected setStatus(value: MailOutboxStatus | 'ALL'): void {
    this.status.set(value);
    this.page.set(0);
  }

  protected setSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }
}
