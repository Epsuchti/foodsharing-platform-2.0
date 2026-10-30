import { ChangeDetectionStrategy, Component, Type, signal } from '@angular/core';

import { ClientSecretComponent } from '../../../oauth/components/client-secret.component';
import { RedirectUrisEditorComponent } from '../../../oauth/components/redirect-uris-editor.component';
import { OAuthScopeListComponent } from '../../../oauth/components/scope-list.component';

/** OAuth provider UI (plan-m7 Q14, C13): scope explanations, the redirect-URI list editor and the one-time secret. */
@Component({
  selector: 'fs-gallery-oauth-section',
  imports: [ClientSecretComponent, OAuthScopeListComponent, RedirectUrisEditorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-oauth">OAuth</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.oauth.scopes">
        <header class="specimen__head"><code>fs-oauth-scope-list</code></header>
        <fs-oauth-scope-list [scopes]="scopes" testId="gallery.oauth.scopeList" />
        <fs-oauth-scope-list [scopes]="scopes" [compact]="true" testId="gallery.oauth.scopeListCompact" />
      </section>
      <section class="specimen" data-testid="gallery.oauth.redirectUris">
        <header class="specimen__head"><code>fs-oauth-redirect-uris-editor</code></header>
        <fs-oauth-redirect-uris-editor [(uris)]="uris" testId="gallery.oauth.redirectUrisEditor" />
      </section>
      <section class="specimen" data-testid="gallery.oauth.secret">
        <header class="specimen__head"><code>fs-oauth-client-secret</code></header>
        <fs-oauth-client-secret clientId="wiki" secret="Zm9vZHNoYXJpbmctZ2FsbGVyeS1kZW1vLXNlY3JldC0wMDAx" />
      </section>
    </div>
  `,
})
export class OAuthGallerySection {
  protected readonly scopes = ['openid', 'profile', 'email', 'regions'];
  protected readonly uris = signal(['https://wiki.foodsharing.example/oauth/callback', 'http://wiki.example.org/callback']);
}

export const OAUTH_GALLERY_SECTIONS: readonly Type<unknown>[] = [OAuthGallerySection];
