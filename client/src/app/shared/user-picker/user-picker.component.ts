import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Observable, of } from 'rxjs';

import { UserSummary, UsersService } from '../../api';
import { loadResource } from '../../core/data/data-state';
import { TPipe } from '../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../data-state';
import { UserChipComponent } from '../user-chip/user-chip.component';

let nextId = 0;

/**
 * User search (name prefix, `searchUsers`, ≥ 2 characters) with an "add" button per hit. Used by the messaging,
 * mailbox, event and survey pickers. `<fs-user-picker [excludeIds]="memberIds" (picked)="add($event)" testId="x" />`
 */
@Component({
  selector: 'fs-user-picker',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label class="label" [for]="inputId">{{ label() ?? ('shared.userPicker.label' | t) }}</label>
    <input
      pInputText
      [id]="inputId"
      type="search"
      autocomplete="off"
      [placeholder]="'shared.userPicker.placeholder' | t"
      [ngModel]="query()"
      (ngModelChange)="query.set($event)"
      [attr.data-testid]="testId() + '.search'"
    />
    @if (query().trim().length >= 2) {
      <fs-data-state
        [state]="hits"
        skeleton="list"
        [compact]="true"
        [emptyTitle]="'shared.userPicker.noMatch' | t"
        [testId]="testId() + '.state'"
        [attr.data-testid]="testId() + '.state'"
      >
        <ng-template [fsData]="hits" let-users>
          <ul class="hits">
            @for (user of visible(users); track user.id) {
              <li [attr.data-testid]="testId() + '.hit'" [attr.data-user-id]="user.id">
                <fs-user-chip [name]="user.displayName" [userId]="user.id" [avatarUrl]="user.avatarUrl" />
                <p-button
                  icon="pi pi-plus"
                  size="small"
                  [outlined]="true"
                  [label]="'shared.userPicker.add' | t"
                  [ariaLabel]="('shared.userPicker.add' | t) + ': ' + user.displayName"
                  (onClick)="pick(user)"
                  [attr.data-testid]="testId() + '.add'"
                />
              </li>
            } @empty {
              <li class="muted">{{ 'shared.userPicker.noMatch' | t }}</li>
            }
          </ul>
        </ng-template>
      </fs-data-state>
    }
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.4rem;
    }
    .label {
      font-weight: 600;
    }
    .hits {
      display: grid;
      gap: 0.35rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .hits li {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .muted {
      color: var(--fs-text-muted);
    }
  `,
})
export class UserPickerComponent {
  private readonly usersApi = inject(UsersService);

  readonly excludeIds = input<readonly string[]>([]);
  readonly regionId = input<string | undefined>(undefined);
  readonly label = input<string | undefined>(undefined);
  readonly testId = input('userPicker');
  /** Custom search (e.g. survey co-author candidates); default `searchUsers` limited to 8 hits. */
  readonly search = input<((q: string) => Observable<UserSummary[]>) | undefined>(undefined);
  readonly picked = output<UserSummary>();

  protected readonly inputId = `fs-user-picker-${nextId++}`;
  protected readonly query = signal('');
  private readonly excluded = computed(() => new Set(this.excludeIds()));

  protected readonly hits = loadResource<UserSummary[], { q: string; regionId?: string }>({
    params: () => {
      const q = this.query().trim();
      return q.length >= 2 ? { q, regionId: this.regionId() } : undefined;
    },
    loader: (params) => {
      if (!params) return of([]);
      const search = this.search();
      return search ? search(params.q) : this.usersApi.searchUsers({ ...params, limit: 8 });
    },
  });

  protected visible(users: UserSummary[]): UserSummary[] {
    return users.filter((user) => !this.excluded().has(user.id));
  }

  protected pick(user: UserSummary): void {
    this.picked.emit(user);
    this.query.set('');
  }
}
