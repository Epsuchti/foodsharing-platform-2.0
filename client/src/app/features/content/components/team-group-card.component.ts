import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { TeamGroup } from '../../../api';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';

/** One working group of the team page (C16): photo or icon, name, teaser and the active members. */
@Component({
  selector: 'fs-team-group-card',
  imports: [TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let g = group();
    <section
      class="fs-card group"
      [attr.data-group]="g.id"
      [attr.aria-labelledby]="'team-' + g.id"
      data-testid="content.team.group"
    >
      <header class="group__head">
        @if (g.photoUrl) {
          <img
            class="group__photo"
            [src]="g.photoUrl"
            alt=""
            width="56"
            height="56"
            loading="lazy"
          />
        } @else {
          <span class="group__photo group__photo--icon" aria-hidden="true"
            ><i class="pi pi-users"></i
          ></span>
        }
        <div class="group__text">
          <h2 class="group__name" [id]="'team-' + g.id" data-testid="content.team.groupName">
            {{ g.name }}
          </h2>
          @if (g.teaser) {
            <p class="group__teaser">{{ g.teaser }}</p>
          }
        </div>
      </header>
      @if (g.members.length) {
        <ul
          class="members"
          [attr.aria-label]="'content.team.members' | t: { count: g.members.length }"
        >
          @for (member of g.members; track $index) {
            <li class="member" data-testid="content.team.member">
              <fs-user-avatar
                [name]="member.displayName"
                [avatarUrl]="member.avatarUrl"
                size="md"
              />
              <span class="member__text">
                <span class="member__name">{{ member.displayName }}</span>
                @if (member.roleDescription) {
                  <span class="member__role">{{ member.roleDescription }}</span>
                }
              </span>
            </li>
          }
        </ul>
      } @else {
        <p class="group__none">{{ 'content.team.noMembers' | t }}</p>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
    }
    .group {
      display: grid;
      gap: 1rem;
      padding: 1.1rem 1.25rem;
    }
    .group__head {
      display: flex;
      align-items: center;
      gap: 0.9rem;
    }
    .group__photo {
      flex: none;
      width: 3.5rem;
      height: 3.5rem;
      border-radius: var(--fs-radius-md);
      object-fit: cover;
    }
    .group__photo--icon {
      display: grid;
      place-items: center;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
      font-size: 1.3rem;
    }
    .group__text {
      display: grid;
      gap: 0.2rem;
      min-width: 0;
    }
    .group__name {
      margin: 0;
      font-size: 1.1rem;
    }
    .group__teaser,
    .group__none {
      margin: 0;
      color: var(--fs-text-muted);
      font-size: 0.9rem;
    }
    .members {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 14rem), 1fr));
      gap: 0.75rem 1rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .member {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      min-width: 0;
    }
    .member__text {
      display: grid;
      min-width: 0;
    }
    .member__name {
      overflow: hidden;
      font-weight: 600;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .member__role {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
  `,
})
export class TeamGroupCardComponent {
  readonly group = input.required<TeamGroup>();
}
