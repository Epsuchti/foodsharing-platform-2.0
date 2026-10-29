import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { PublicProfile } from '../../../api';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { TPipe } from '../../../core/i18n/t.pipe';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { photoInSize } from '../profiles.util';

/**
 * Head of a profile (A6): large photo, name, role, verified mark, "member since", the foodsaver number for
 * administrators (`permissions.mayAdminister`), the sleep banner (A39) and the public / internal "about me".
 * The actions are projected (`<fs-profile-card [profile]="p">…buttons…</fs-profile-card>`).
 */
@Component({
  selector: 'fs-profile-card',
  imports: [DateTimePipe, TPipe, UserAvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = profile();
    <section class="fs-card card" data-testid="profiles.page.card" [attr.aria-labelledby]="headingId()">
      <div class="head">
        <fs-user-avatar
          class="head__photo"
          [name]="p.user.displayName"
          [avatarUrl]="photo()"
          [role]="p.user.role"
          [colorSeed]="p.user.id"
          [sleeping]="!!p.user.sleepMode"
          size="xl"
          data-testid="profiles.page.photo"
        />
        <div class="head__who">
          <h1 class="head__name" [id]="headingId()" data-testid="profiles.page.name">
            {{ p.user.displayName }}
            @if (p.user.verified) {
              <i class="pi pi-verified head__verified" role="img" [attr.aria-label]="'profiles.card.verified' | t"></i>
            }
          </h1>
          <p class="head__meta">
            <span class="role" data-testid="profiles.page.role">{{ 'common.role.' + p.user.role | t }}</span>
            @if (p.user.homeRegion; as home) {
              <span><i class="pi pi-map-marker" aria-hidden="true"></i> {{ home.name }}</span>
            }
            @if (p.registeredAt) {
              <span>{{ 'profiles.card.memberSince' | t: { date: (p.registeredAt | fsDate: 'dateLong') } }}</span>
            }
          </p>
          @if (p.permissions.mayAdminister && p.user.foodsaverNumber) {
            <p class="head__number" data-testid="profiles.page.foodsaverNumber">
              <i class="pi pi-id-card" aria-hidden="true"></i>
              {{ 'profiles.card.foodsaverNumber' | t: { number: '' + p.user.foodsaverNumber } }}
            </p>
          }
          <div class="head__actions"><ng-content /></div>
        </div>
      </div>

      @if (p.user.sleepMode) {
        <div class="sleep" role="status" data-testid="profiles.page.sleep">
          <i class="pi pi-moon" aria-hidden="true"></i>
          <div>
            <strong>{{ 'profiles.card.sleeping' | t: { name: p.user.firstName || p.user.displayName } }}</strong>
            @if (p.user.sleepMessage) {
              <p data-testid="profiles.page.sleepMessage">{{ p.user.sleepMessage }}</p>
            }
          </div>
        </div>
      }

      @if (p.aboutMe) {
        <div class="about" data-testid="profiles.page.about">
          <h2>{{ 'profiles.card.aboutMe' | t }}</h2>
          <p>{{ p.aboutMe }}</p>
        </div>
      }
      @if (p.aboutMeInternal) {
        <div class="about about--internal" data-testid="profiles.page.aboutInternal">
          <h2><i class="pi pi-lock" aria-hidden="true"></i> {{ 'profiles.card.aboutMeInternal' | t }}</h2>
          <p>{{ p.aboutMeInternal }}</p>
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .card { display: grid; gap: 1rem; padding: 1.25rem; margin: 0; }
    .head { display: flex; flex-wrap: wrap; gap: 1rem 1.25rem; align-items: center; }
    .head__who { display: grid; gap: 0.35rem; flex: 1 1 16rem; min-width: 0; }
    .head__name { display: flex; align-items: center; gap: 0.4rem; margin: 0; font-size: 1.5rem; overflow-wrap: anywhere; }
    .head__verified { color: var(--fs-primary); font-size: 1.1rem; }
    .head__meta { display: flex; flex-wrap: wrap; gap: 0.25rem 0.9rem; margin: 0; color: var(--fs-text-muted); font-size: 0.9rem; }
    .role { font-weight: 650; color: var(--fs-primary-strong); }
    .head__number { display: flex; align-items: center; gap: 0.4rem; margin: 0; font-size: 0.9rem; }
    .head__actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.35rem; }
    .head__actions:empty { display: none; }
    .sleep {
      display: flex; gap: 0.75rem; align-items: flex-start; padding: 0.75rem 1rem;
      border: 1px solid var(--fs-border); border-radius: var(--fs-radius-md); background: var(--fs-surface-muted);
    }
    .sleep .pi { margin-top: 0.2rem; font-size: 1.1rem; }
    .sleep p { margin: 0.25rem 0 0; white-space: pre-line; }
    .about h2 { display: flex; align-items: center; gap: 0.4rem; margin: 0 0 0.35rem; font-size: 1rem; }
    .about p { margin: 0; white-space: pre-line; overflow-wrap: anywhere; }
    .about--internal { padding: 0.75rem 1rem; border: 1px dashed var(--fs-border); border-radius: var(--fs-radius-md); }
  `,
})
export class ProfileCardComponent {
  readonly profile = input.required<PublicProfile>();

  protected readonly photo = computed(() => photoInSize(this.profile().photoUrl ?? this.profile().user.avatarUrl, 'lg'));
  protected readonly headingId = computed(() => `profile-name-${this.profile().user.id}`);
}
