import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { Button } from '@openng/optimus-ui/button';

import { Achievement } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { achievementIcon } from '../../profiles/profiles.util';
import { mayDelete } from '../achievements.util';

/**
 * One achievement definition: icon, name, description, visibility, duplicate mode, validity and how many members hold
 * it. Administrators get award, awards list, edit and delete (not for system achievements).
 */
@Component({
  selector: 'fs-achievement-card',
  imports: [Button, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'fs-card',
    'data-testid': 'achievements.card',
    '[attr.data-achievement-id]': 'achievement().id',
    '[attr.data-visibility]': 'achievement().visibility',
  },
  template: `
    @let a = achievement();
    <div class="head">
      <span class="icon" aria-hidden="true"><i [class]="icon()"></i></span>
      <div class="title">
        <h3 class="name" data-testid="achievements.card.name">{{ a.name }}</h3>
        <div class="tags">
          <span class="tag" [class.tag--hidden]="a.visibility === 'HIDDEN'" data-testid="achievements.card.visibility">
            <i class="pi pi-eye" aria-hidden="true"></i>{{ 'achievements.visibility.' + a.visibility | t }}
          </span>
          <span class="tag" data-testid="achievements.card.duplicate">{{ 'achievements.duplicate.' + a.duplicateMode | t }}</span>
          @if (a.systemKey) {
            <span class="tag tag--system" data-testid="achievements.card.system">
              <i class="pi pi-cog" aria-hidden="true"></i>{{ 'achievements.card.system' | t }}
            </span>
          }
        </div>
      </div>
    </div>
    @if (a.description) {
      <p class="description" data-testid="achievements.card.description">{{ a.description }}</p>
    }
    <p class="meta">
      <span data-testid="achievements.card.count">{{ 'achievements.card.awarded' | t: { count: a.awardedCount } }}</span>
      <span>·</span>
      <span data-testid="achievements.card.validity">
        {{ (a.validityDays ? 'achievements.card.validity' : 'achievements.card.unlimited') | t: { days: a.validityDays ?? 0 } }}
      </span>
    </p>
    @if (a.mayAdminister) {
      <div class="actions">
        <p-button
          [label]="'achievements.card.award' | t"
          icon="pi pi-user-plus"
          size="small"
          (onClick)="award.emit()"
          [ariaLabel]="('achievements.card.award' | t) + ': ' + a.name"
          data-testid="achievements.card.award"
        />
        <p-button
          [label]="'achievements.card.awards' | t"
          icon="pi pi-list"
          size="small"
          severity="secondary"
          [outlined]="true"
          (onClick)="awards.emit()"
          [ariaLabel]="('achievements.card.awards' | t) + ': ' + a.name"
          data-testid="achievements.card.awards"
        />
        <span class="spacer"></span>
        <p-button
          icon="pi pi-pencil"
          size="small"
          severity="secondary"
          [text]="true"
          [rounded]="true"
          (onClick)="edit.emit()"
          [ariaLabel]="('achievements.card.edit' | t) + ': ' + a.name"
          data-testid="achievements.card.edit"
        />
        @if (deletable()) {
          <p-button
            icon="pi pi-trash"
            size="small"
            severity="danger"
            [text]="true"
            [rounded]="true"
            (onClick)="remove.emit()"
            [ariaLabel]="('achievements.card.delete' | t) + ': ' + a.name"
            data-testid="achievements.card.delete"
          />
        }
      </div>
    }
  `,
  styles: `
    :host { display: grid; gap: 0.6rem; align-content: start; padding: 1rem; }
    .head { display: flex; gap: 0.75rem; align-items: flex-start; }
    .icon {
      display: grid; place-items: center; flex: none; width: 2.75rem; height: 2.75rem; border-radius: 50%;
      background: var(--fs-primary-soft); color: var(--fs-primary-strong); font-size: 1.25rem;
    }
    .title { display: grid; gap: 0.35rem; min-width: 0; }
    .name { margin: 0; font-size: 1.02rem; line-height: 1.3; overflow-wrap: anywhere; }
    .tags { display: flex; flex-wrap: wrap; gap: 0.3rem; }
    .tag {
      display: inline-flex; align-items: center; gap: 0.3rem; padding: 0.1rem 0.5rem; border: 1px solid var(--fs-border);
      border-radius: 999px; color: var(--fs-text-muted); font-size: 0.78rem; font-weight: 600;
    }
    .tag i { font-size: 0.75rem; }
    .tag--hidden { border-style: dashed; }
    .tag--system { color: var(--fs-text); }
    .description { margin: 0; color: var(--fs-text); font-size: 0.9rem; white-space: pre-line; }
    .meta { display: flex; flex-wrap: wrap; gap: 0.35rem; margin: 0; color: var(--fs-text-muted); font-size: 0.84rem; }
    .actions { display: flex; flex-wrap: wrap; gap: 0.4rem; align-items: center; }
    .spacer { flex: 1; }
  `,
})
export class AchievementCardComponent {
  readonly achievement = input.required<Achievement>();
  readonly award = output<void>();
  readonly awards = output<void>();
  readonly edit = output<void>();
  readonly remove = output<void>();

  protected readonly icon = computed(() => achievementIcon(this.achievement().icon));
  protected readonly deletable = computed(() => mayDelete(this.achievement()));
}
