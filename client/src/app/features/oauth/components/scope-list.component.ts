import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TPipe } from '../../../core/i18n/t.pipe';
import { orderedScopes, scopeIcon } from '../oauth.util';

/**
 * What an OAuth scope gives the application, in plain words (consent page, connections, gallery). `compact` shows
 * the titles only.
 */
@Component({
  selector: 'fs-oauth-scope-list',
  imports: [TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="scopes" [class.scopes--compact]="compact()" [attr.data-testid]="testId()">
      @for (scope of items(); track scope.name) {
        <li class="scope" [attr.data-scope]="scope.name" [attr.data-testid]="testId() + '.item'">
          <span class="scope__icon" aria-hidden="true"><i [class]="scope.icon"></i></span>
          <span class="scope__body">
            <span class="scope__title">{{ 'oauth.scope.' + scope.name + '.title' | t }}</span>
            @if (!compact()) {
              <span class="scope__text">{{ 'oauth.scope.' + scope.name + '.text' | t }}</span>
            }
          </span>
        </li>
      }
    </ul>
  `,
  styles: `
    .scopes {
      display: grid;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .scopes--compact {
      display: flex;
      flex-wrap: wrap;
      gap: 0.4rem;
    }
    .scope {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
    }
    .scope__icon {
      display: inline-grid;
      flex: none;
      place-items: center;
      width: 2.25rem;
      height: 2.25rem;
      border-radius: 50%;
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
    .scope__body {
      display: grid;
      gap: 0.1rem;
    }
    .scope__title {
      font-weight: 650;
    }
    .scope__text {
      color: var(--fs-text-muted);
      font-size: 0.875rem;
    }
    .scopes--compact .scope {
      align-items: center;
      gap: 0.35rem;
      padding: 0.15rem 0.6rem 0.15rem 0.2rem;
      border-radius: 999px;
      background: var(--fs-surface-muted);
      font-size: 0.8125rem;
    }
    .scopes--compact .scope__icon {
      width: 1.5rem;
      height: 1.5rem;
      font-size: 0.75rem;
    }
    .scopes--compact .scope__title {
      font-weight: 600;
    }
  `,
})
export class OAuthScopeListComponent {
  readonly scopes = input.required<readonly string[]>();
  readonly compact = input(false);
  readonly testId = input('oauth.scopes');

  protected readonly items = computed(() =>
    orderedScopes(this.scopes()).map((name) => ({ name, icon: scopeIcon(name) })),
  );
}
