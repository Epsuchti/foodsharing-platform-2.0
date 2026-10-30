import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';

import { Achievement, AchievementsService, AwardCreate } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  AchievementFormStart,
  AchievementFormValue,
  achievementFormValue,
  toAchievementCreate,
  toAchievementUpdate,
} from '../achievements.util';
import { AchievementCardComponent } from './achievement-card.component';
import { AchievementFormDialogComponent } from './achievement-form-dialog.component';
import { AwardDialogComponent } from './award-dialog.component';
import { AwardsDialogComponent } from './awards-dialog.component';

/**
 * Achievement grid of a region (`regionId`) or the global ones (`regionId` null), shared by the region tab and
 * `/admin/achievements` (AC1). Members see every definition but HIDDEN ones; administrators (`mayDefine` for new ones,
 * `Achievement.mayAdminister` per card) define, edit, delete, award and manage awards. `undefined` = region not known yet.
 */
@Component({
  selector: 'fs-achievements-board',
  imports: [
    ...DATA_STATE_IMPORTS,
    AchievementCardComponent,
    AchievementFormDialogComponent,
    AwardDialogComponent,
    AwardsDialogComponent,
    Button,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="head">
      <p class="intro">{{ (regionId() ? 'achievements.board.introRegion' : 'achievements.board.introGlobal') | t }}</p>
      @if (mayDefine()) {
        <p-button [label]="'achievements.board.define' | t" icon="pi pi-plus" (onClick)="openCreate()" data-testid="achievements.board.define" />
      }
    </div>
    <fs-data-state
      [state]="list"
      skeleton="card"
      [emptyTitle]="'achievements.board.empty' | t"
      [emptyText]="(mayDefine() ? 'achievements.board.emptyAdmin' : 'achievements.board.emptyMember') | t"
      emptyIcon="pi pi-trophy"
      testId="achievements.board.state"
      data-testid="achievements.board.state"
    >
      <ng-template [fsData]="list" let-achievements>
        <ul class="grid" data-testid="achievements.board.list">
          @for (achievement of achievements; track achievement.id) {
            <li>
              <fs-achievement-card
                [achievement]="achievement"
                (award)="openAward(achievement)"
                (awards)="openAwards(achievement)"
                (edit)="openEdit(achievement)"
                (remove)="askDelete(achievement)"
              />
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>

    <fs-achievement-form-dialog [(visible)]="formOpen" [start]="formStart()" [busy]="save.busy()" (save)="save.run($event)" />
    <fs-award-dialog [(visible)]="awardOpen" [achievement]="selected()" [busy]="award.busy()" (save)="award.run($event)" />
    <fs-awards-dialog [(visible)]="awardsOpen" [achievement]="selected()" (changed)="list.reload()" />
  `,
  styles: `
    :host { display: block; }
    .head { display: flex; flex-wrap: wrap; gap: 0.75rem; align-items: center; justify-content: space-between; margin-bottom: 1rem; }
    .intro { margin: 0; max-width: 42rem; color: var(--fs-text-muted); font-size: 0.9rem; }
    .grid {
      display: grid; grid-template-columns: repeat(auto-fill, minmax(min(100%, 19rem), 1fr)); gap: 1rem;
      margin: 0; padding: 0; list-style: none;
    }
    .grid li { display: grid; }
  `,
})
export class AchievementsBoardComponent {
  private readonly api = inject(AchievementsService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly session = inject(SessionService);

  /** Region of the grid; null = global achievements; undefined = not known yet. */
  readonly regionId = input<string | null | undefined>(undefined);
  /** May define new achievements here (region administrators, ORGA for global ones). */
  readonly mayDefine = input(false);

  protected readonly formOpen = signal(false);
  protected readonly awardOpen = signal(false);
  protected readonly awardsOpen = signal(false);
  protected readonly selected = signal<Achievement | null>(null);
  protected readonly formStart = signal<AchievementFormStart | null>(null);

  private readonly scope = computed(() => {
    const regionId = this.regionId();
    return regionId === undefined ? undefined : { regionId };
  });

  protected readonly list = loadResource<Achievement[], { regionId: string | null }>({
    params: () => this.scope(),
    loader: ({ regionId }) => this.api.listAchievements(regionId ? { regionId } : {}),
  });

  protected readonly save = runMutation(
    (value: AchievementFormValue) => {
      const editing = this.selected();
      const start = this.formStart();
      return editing && start
        ? this.api.updateAchievement({ achievementId: editing.id, achievementUpdate: toAchievementUpdate(value, start.baseKnown) })
        : this.api.createAchievement({ achievementCreate: toAchievementCreate(value, this.regionId() ?? null) });
    },
    {
      successToastKey: 'achievements.board.saved',
      onSuccess: () => {
        this.formOpen.set(false);
        this.list.reload();
      },
    },
  );

  protected readonly award = runMutation(
    (body: AwardCreate) => this.api.awardAchievement({ achievementId: this.selected()!.id, awardCreate: body }),
    {
      successToastKey: 'achievements.award.done',
      onSuccess: () => {
        this.awardOpen.set(false);
        this.list.reload();
      },
    },
  );

  protected readonly remove = runMutation((achievement: Achievement) => this.api.deleteAchievement({ achievementId: achievement.id }), {
    successToastKey: 'achievements.board.deleted',
    onSuccess: () => this.list.reload(),
  });

  protected openCreate(): void {
    this.selected.set(null);
    this.formStart.set(null);
    this.formOpen.set(true);
  }

  protected openEdit(achievement: Achievement): void {
    const language = this.session.user()?.language ?? this.i18n.language();
    this.selected.set(achievement);
    this.formStart.set(achievementFormValue(achievement, language));
    this.formOpen.set(true);
  }

  protected openAward(achievement: Achievement): void {
    this.selected.set(achievement);
    this.awardOpen.set(true);
  }

  protected openAwards(achievement: Achievement): void {
    this.selected.set(achievement);
    this.awardsOpen.set(true);
  }

  protected askDelete(achievement: Achievement): void {
    this.confirm.confirm({
      header: this.i18n.t('achievements.board.deleteTitle', { name: achievement.name }),
      message: this.i18n.t('achievements.board.deleteText', { count: achievement.awardedCount }),
      acceptLabel: this.i18n.t('achievements.card.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(achievement),
    });
  }
}
