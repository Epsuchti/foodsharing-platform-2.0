import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Paginator, PaginatorState } from '@openng/optimus-ui/paginator';
import { Select } from '@openng/optimus-ui/select';
import { debounceTime, distinctUntilChanged, Subject } from 'rxjs';

import {
  GroupsService,
  RegionAdminItem,
  RegionAdminPage,
  RegionAdminService,
  RegionRef,
  RegionsImportPreview,
  WorkingGroupFunction,
  WorkingGroupSummary,
} from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { CsvDropzoneComponent, CsvFile } from '../../../shared/csv-dropzone/csv-dropzone.component';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { RegionFormDialogComponent, RegionFormValue } from '../components/region-form-dialog.component';

export const REGION_ADMIN_PAGE_SIZE = 25;

/**
 * /admin/regions (R7, ORGA): browse the region tree level by level (or search), create children, edit, move,
 * delete with reason, assign working-group functions and import regions from CSV (dry run preview first).
 */
@Component({
  selector: 'fs-region-admin-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    CsvDropzoneComponent,
    Dialog,
    FormsModule,
    InputText,
    PageHeaderComponent,
    Paginator,
    RegionFormDialogComponent,
    RegionPickerComponent,
    RequiredMessageDialogComponent,
    Select,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './region-admin-page.component.html',
  styleUrl: './region-admin-page.component.scss',
})
export class RegionAdminPageComponent {
  private readonly api = inject(RegionAdminService);
  private readonly groupsApi = inject(GroupsService);
  private readonly i18n = inject(I18nService);

  protected readonly pageSize = REGION_ADMIN_PAGE_SIZE;
  /** Drill-down path from the top level; the last entry is the listed parent. */
  protected readonly path = signal<RegionRef[]>([]);
  protected readonly parent = computed(() => this.path().at(-1) ?? null);
  protected readonly query = signal('');
  protected readonly page = signal(0);
  private readonly search$ = new Subject<string>();

  protected readonly regions = loadResource<RegionAdminPage, { parentId?: string; q?: string; page: number }>({
    params: () => {
      const q = this.query().trim();
      return q ? { q, page: this.page() } : { parentId: this.parent()?.id, page: this.page() };
    },
    loader: (params) => this.api.listRegionsForAdmin({ ...params, size: REGION_ADMIN_PAGE_SIZE }),
  });

  constructor() {
    this.search$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((q) => {
      this.page.set(0);
      this.query.set(q);
    });
  }

  protected onSearch(value: string): void {
    this.search$.next(value);
  }

  protected open(item: RegionAdminItem): void {
    this.query.set('');
    this.page.set(0);
    this.path.update((path) => [...path, item.region]);
  }

  protected goTo(index: number): void {
    this.page.set(0);
    this.path.update((path) => path.slice(0, index));
  }

  protected ambassadorNames(item: RegionAdminItem): string {
    return item.ambassadors.map((user) => user.displayName).join(', ');
  }

  protected onPage(event: PaginatorState): void {
    this.page.set(event.page ?? 0);
  }

  // --- create / edit ---
  protected readonly formOpen = signal(false);
  protected readonly editing = signal<RegionAdminItem | null>(null);

  protected openCreate(): void {
    this.editing.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(item: RegionAdminItem): void {
    this.editing.set(item);
    this.formOpen.set(true);
  }

  protected readonly save = runMutation(
    (value: RegionFormValue) => {
      const item = this.editing();
      return item
        ? this.api.updateRegion({ regionId: item.region.id, regionAdminUpdate: value })
        : this.api.createRegion({ regionForAdministration: { ...value, parentId: this.parent()!.id } });
    },
    {
      successToastKey: 'regionAdmin.saved',
      onSuccess: () => {
        this.formOpen.set(false);
        this.regions.reload();
      },
    },
  );

  // --- move ---
  protected readonly moveTarget = signal<RegionAdminItem | null>(null);
  protected readonly moveOpen = signal(false);
  protected readonly moveParent = signal<RegionRef | null>(null);
  protected readonly moveReason = signal('');

  protected openMove(item: RegionAdminItem): void {
    this.moveTarget.set(item);
    this.moveParent.set(null);
    this.moveReason.set('');
    this.moveOpen.set(true);
  }

  protected readonly move = runMutation(
    () =>
      this.api.moveRegion({
        regionId: this.moveTarget()!.region.id,
        moveRegionRequest: { newParentId: this.moveParent()!.id, reason: this.moveReason().trim() || undefined },
      }),
    {
      successToastKey: 'regionAdmin.moved',
      onSuccess: () => {
        this.moveOpen.set(false);
        this.regions.reload();
      },
    },
  );

  // --- delete ---
  protected readonly deleteTarget = signal<RegionAdminItem | null>(null);
  protected readonly deleteOpen = signal(false);

  protected openDelete(item: RegionAdminItem): void {
    this.deleteTarget.set(item);
    this.deleteOpen.set(true);
  }

  protected readonly remove = runMutation(
    (reason: string) => this.api.deleteRegion({ regionId: this.deleteTarget()!.region.id, reasonBody: { reason } }),
    {
      successToastKey: 'regionAdmin.deleted',
      onSuccess: () => {
        this.deleteOpen.set(false);
        this.regions.reload();
      },
    },
  );

  // --- functions ---
  protected readonly functionsTarget = signal<RegionAdminItem | null>(null);
  protected readonly functionsOpen = signal(false);
  protected readonly newFunction = signal<WorkingGroupFunction | null>(null);
  protected readonly newGroupId = signal<string | null>(null);

  protected readonly functionOptions = computed(() => {
    this.i18n.language();
    return Object.values(WorkingGroupFunction).map((value) => ({
      value,
      label: this.i18n.t(`regionAdmin.function.${value}`),
    }));
  });

  /** Region whose groups the function dialog offers (a computed, so refreshing the target does not reload them). */
  private readonly functionsRegionId = computed(() =>
    this.functionsOpen() ? this.functionsTarget()?.region.id : undefined,
  );

  protected readonly groups = loadResource<WorkingGroupSummary[], string>({
    params: () => this.functionsRegionId(),
    loader: (regionId) => this.groupsApi.listGroupsInRegion({ regionId }),
  });

  protected readonly groupOptions = computed(() =>
    (this.groups.data() ?? []).map((group) => ({ value: group.id, label: group.name })),
  );

  protected openFunctions(item: RegionAdminItem): void {
    this.functionsTarget.set(item);
    this.newFunction.set(null);
    this.newGroupId.set(null);
    this.functionsOpen.set(true);
  }

  private refreshTarget(): void {
    const id = this.functionsTarget()?.region.id;
    if (id) {
      this.reloadTarget.run(id);
    }
  }

  protected readonly reloadTarget = runMutation((regionId: string) => this.api.getRegionForAdmin({ regionId }), {
    onSuccess: (item) => {
      this.functionsTarget.set(item);
      this.regions.reload();
    },
  });

  protected readonly setFunction = runMutation(
    () =>
      this.api.setRegionFunction({
        regionId: this.functionsTarget()!.region.id,
        _function: this.newFunction()!,
        regionFunctionUpdate: { groupId: this.newGroupId()! },
      }),
    {
      successToastKey: 'regionAdmin.functionSaved',
      onSuccess: () => {
        this.newFunction.set(null);
        this.newGroupId.set(null);
        this.refreshTarget();
      },
    },
  );

  protected readonly removeFunction = runMutation(
    (fn: WorkingGroupFunction) =>
      this.api.removeRegionFunction({ regionId: this.functionsTarget()!.region.id, _function: fn }),
    { successToastKey: 'regionAdmin.functionRemoved', onSuccess: () => this.refreshTarget() },
  );

  // --- CSV import ---
  protected readonly importOpen = signal(false);
  protected readonly csv = signal<CsvFile | null>(null);
  protected readonly preview = signal<RegionsImportPreview | null>(null);

  protected openImport(): void {
    this.csv.set(null);
    this.preview.set(null);
    this.importOpen.set(true);
  }

  protected readonly runImport = runMutation(
    (dryRun: boolean) => this.api.importRegionsCsv({ regionsCsvImport: { csv: this.csv()!.text, dryRun } }),
    {
      onSuccess: (result) => {
        this.preview.set(result);
        if (!result.dryRun) {
          this.importOpen.set(false);
          this.regions.reload();
        }
      },
    },
  );

  protected onCsv(file: CsvFile): void {
    this.csv.set(file);
    this.preview.set(null);
    void this.runImport.run(true);
  }
}
