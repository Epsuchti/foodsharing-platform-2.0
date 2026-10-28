import { ChangeDetectionStrategy, Component, inject, input, model, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';

import { RegionMemberPage, RegionsService, UserSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

/** Search a newcomer of the region (name or foodsaver ID) and pick them, e.g. to book on their behalf (EA12). */
@Component({
  selector: 'fs-member-pick-dialog',
  imports: [...DATA_STATE_IMPORTS, Button, Dialog, FormsModule, InputText, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [visible]="visible()"
      (visibleChange)="visible.set($event)"
      [modal]="true"
      [draggable]="false"
      [header]="title()"
      [style]="{ width: 'min(32rem, 96vw)' }"
    >
      <div class="stack" data-testid="introPickups.pick">
        <div class="field">
          <label for="pick-search">{{ 'introPickups.pick.search' | t }}</label>
          <input pInputText id="pick-search" type="search" [ngModel]="input()" (ngModelChange)="onInput($event)" [placeholder]="'introPickups.pick.placeholder' | t" data-testid="introPickups.pick.search" />
        </div>
        <fs-data-state [state]="members" skeleton="list" [skeletonRows]="3" [compact]="true" emptyIcon="pi pi-user" [emptyTitle]="'introPickups.pick.empty' | t" data-testid="introPickups.pick.state">
          <ng-template [fsData]="members" let-page>
            <ul class="list">
              @for (member of page.items; track member.user.id) {
                <li class="pick">
                  <fs-user-chip [name]="member.user.displayName" [avatarUrl]="member.user.avatarUrl" [foodsaverNumber]="member.user.foodsaverNumber" />
                  <p-button [label]="'introPickups.pick.choose' | t" size="small" [outlined]="true" [loading]="busy()" (onClick)="picked.emit(member.user)" data-testid="introPickups.pick.choose" />
                </li>
              }
            </ul>
          </ng-template>
        </fs-data-state>
      </div>
    </p-dialog>
  `,
  styleUrl: '../intro-pickups.scss',
  styles: `
    :host {
      display: contents;
    }
    .pick {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
  `,
})
export class MemberPickDialogComponent {
  private readonly api = inject(RegionsService);
  readonly visible = model(false);
  readonly regionId = input<string | null>(null);
  readonly title = input('');
  readonly busy = input(false);
  readonly picked = output<UserSummary>();

  protected readonly input = signal('');
  private readonly search = signal('');
  private timer: ReturnType<typeof setTimeout> | undefined;

  protected readonly members = loadResource<RegionMemberPage, { regionId: string; search: string } | undefined>({
    params: () => {
      const regionId = this.regionId();
      return this.visible() && regionId ? { regionId, search: this.search() } : undefined;
    },
    loader: (params) =>
      this.api.listRegionMembers({ regionId: params!.regionId, search: params!.search || undefined, verified: false, size: 20 }),
  });

  protected onInput(value: string): void {
    this.input.set(value);
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.search.set(value.trim()), 300);
  }
}
