import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { GroupCategory, GroupsService, WorkingGroupSummary } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { RegionPageContext } from '../region-page.context';
import { ApplyDialogComponent } from '../shared/apply-dialog.component';
import { GroupFormDialogComponent, GroupFormValue } from '../shared/group-form-dialog.component';

/**
 * Groups tab (R9): working groups of the region with category filter and archived toggle; join open groups,
 * apply to others (dialog), ambassadors create groups.
 */
@Component({
  selector: 'fs-region-groups-tab',
  imports: [
    ...DATA_STATE_IMPORTS,
    ApplyDialogComponent,
    Button,
    FormsModule,
    GroupFormDialogComponent,
    RouterLink,
    Select,
    TPipe,
    ToggleSwitch,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar">
      <div class="field">
        <label for="groups-category">{{ 'regions.groups.category' | t }}</label>
        <p-select
          inputId="groups-category"
          [options]="categoryOptions()"
          optionLabel="label"
          optionValue="value"
          [ngModel]="category()"
          (ngModelChange)="category.set($event)"
          [showClear]="true"
          [placeholder]="'regions.groups.allCategories' | t"
          appendTo="body"
          data-testid="regions.groups.category"
        />
      </div>
      <div class="archived">
        <p-toggleswitch inputId="groups-archived" [ngModel]="archived()" (ngModelChange)="archived.set($event)" data-testid="regions.groups.archived" />
        <label for="groups-archived">{{ 'regions.groups.showArchived' | t }}</label>
      </div>
      @if (ctx.isAdmin()) {
        <p-button class="create" [label]="'regions.groups.create' | t" icon="pi pi-plus" (onClick)="formOpen.set(true)" data-testid="regions.groups.create" />
      }
    </div>

    <fs-data-state
      [state]="groups"
      skeleton="card"
      emptyIcon="pi pi-sitemap"
      emptyIllustration="inbox"
      [emptyTitle]="'regions.groups.empty' | t"
      testId="regions.groups.list"
      data-testid="regions.groups.list"
    >
      <ng-template [fsData]="groups">
        @if (visible().length === 0) {
          <p class="muted">{{ 'regions.groups.noMatch' | t }}</p>
        }
        <ul class="grid">
          @for (group of visible(); track group.id) {
            <li class="fs-card group" [attr.data-group]="group.slug" data-testid="regions.groups.group">
              <div class="group__head">
                <span class="group__icon" aria-hidden="true">
                  <i class="pi" [class.pi-sitemap]="!group.function" [class.pi-bolt]="!!group.function"></i>
                </span>
                <div class="group__title">
                  <a [routerLink]="['/groups', group.id]" class="group__name" [attr.data-testid]="'regions.groups.link.' + group.slug">{{ group.name }}</a>
                  <span class="group__meta">
                    {{ 'regions.category.' + group.category | t }} ·
                    {{ 'regions.groups.members' | t: { count: group.memberCount } }}
                  </span>
                </div>
              </div>
              @if (group.teaser) {
                <p class="group__teaser">{{ group.teaser }}</p>
              }
              <div class="group__tags">
                @if (group.function) {
                  <span class="tag tag--function">{{ 'regions.function.' + group.function | t }}</span>
                }
                <span class="tag">{{ 'regions.applyType.' + group.applyType | t }}</span>
                @if (group.membership.member) {
                  <span class="tag tag--member">{{ (group.membership.admin ? 'regions.groups.admin' : 'regions.groups.member') | t }}</span>
                } @else if (group.membership.applied) {
                  <span class="tag tag--applied">{{ 'regions.groups.applied' | t }}</span>
                }
              </div>
              @if (!group.membership.member) {
                <div class="group__actions">
                  @if (group.mayJoin) {
                    <p-button
                      [label]="'regions.groups.join' | t"
                      icon="pi pi-sign-in"
                      size="small"
                      [loading]="join.busy()"
                      (onClick)="join.run(group)"
                      [attr.data-testid]="'regions.groups.join.' + group.slug"
                    />
                  } @else if (group.mayApply && !group.membership.applied) {
                    <p-button
                      [label]="'regions.groups.apply' | t"
                      icon="pi pi-send"
                      size="small"
                      severity="secondary"
                      [outlined]="true"
                      (onClick)="openApply(group)"
                      [attr.data-testid]="'regions.groups.apply.' + group.slug"
                    />
                  }
                </div>
              }
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>

    <fs-apply-dialog [(visible)]="applyOpen" [group]="applyTarget()" (applied)="groups.reload()" />
    <fs-group-form-dialog [(visible)]="formOpen" [allowRestricted]="ctx.isOrga()" [busy]="create.busy()" (save)="create.run($event)" />
  `,
  styleUrl: './tab.scss',
  styles: `
    .archived {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding-bottom: 0.4rem;
      font-size: 0.9rem;
    }
    .create {
      margin-left: auto;
    }
    .grid {
      display: grid;
      gap: 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
      @media (min-width: 48rem) {
        grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
      }
    }
    .group {
      display: grid;
      align-content: start;
      gap: 0.7rem;
      padding: 1.1rem;
    }
    .group__head {
      display: flex;
      gap: 0.75rem;
    }
    .group__icon {
      display: grid;
      width: 2.5rem;
      height: 2.5rem;
      flex: 0 0 auto;
      place-items: center;
      border-radius: var(--fs-radius-sm);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .group__title {
      display: grid;
      min-width: 0;
    }
    .group__name {
      color: var(--fs-text);
      font-weight: 700;
      text-decoration: none;
      overflow-wrap: anywhere;
    }
    .group__name:hover {
      color: var(--fs-primary-strong);
      text-decoration: underline;
    }
    .group__meta {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .group__teaser {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .group__tags {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .tag {
      padding: 0.1rem 0.55rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.75rem;
      font-weight: 650;
    }
    .tag--function {
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
    .tag--member {
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .tag--applied {
      background: var(--fs-surface-muted);
      color: var(--fs-text);
      font-style: italic;
    }
    .group__actions {
      display: flex;
      gap: 0.5rem;
    }
  `,
})
export class RegionGroupsTab {
  protected readonly ctx = inject(RegionPageContext);
  private readonly api = inject(GroupsService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly category = signal<GroupCategory | null>(null);
  protected readonly archived = signal(false);
  protected readonly applyOpen = signal(false);
  protected readonly applyTarget = signal<WorkingGroupSummary | null>(null);
  protected readonly formOpen = signal(false);

  protected readonly categoryOptions = computed(() => {
    this.i18n.language();
    return Object.values(GroupCategory).map((value) => ({ value, label: this.i18n.t(`regions.category.${value}`) }));
  });

  protected readonly groups = loadResource<WorkingGroupSummary[], { regionId: string; includeArchived: boolean }>({
    params: () => {
      const regionId = this.ctx.regionId();
      return regionId ? { regionId, includeArchived: this.archived() } : undefined;
    },
    loader: (params) => this.api.listGroupsInRegion(params),
  });

  protected readonly visible = computed(() => {
    const category = this.category();
    return (this.groups.data() ?? []).filter((group) => !category || group.category === category);
  });

  protected readonly join = runMutation((group: WorkingGroupSummary) => this.api.joinOpenGroup({ groupId: group.id }), {
    successToastKey: 'regions.groups.joined',
    onSuccess: () => this.groups.reload(),
  });

  protected readonly create = runMutation(
    (value: GroupFormValue) => this.api.createGroup({ regionId: this.ctx.regionId()!, workingGroupCreate: value }),
    {
      successToastKey: 'regions.groups.created',
      onSuccess: (group) => {
        this.formOpen.set(false);
        void this.router.navigate(['/groups', group.id]);
      },
    },
  );

  protected openApply(group: WorkingGroupSummary): void {
    this.applyTarget.set(group);
    this.applyOpen.set(true);
  }
}
