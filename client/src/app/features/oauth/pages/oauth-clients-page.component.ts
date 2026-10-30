import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { Dialog } from '@openng/optimus-ui/dialog';
import { InputText } from '@openng/optimus-ui/inputtext';
import { RadioButton } from '@openng/optimus-ui/radiobutton';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { AdminOauthService, OAuthClient, RegionRef } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { RegionPickerComponent } from '../../../shared/region-picker/region-picker.component';
import { ClientSecretComponent } from '../components/client-secret.component';
import { RedirectUrisEditorComponent } from '../components/redirect-uris-editor.component';
import { OAuthScopeListComponent } from '../components/scope-list.component';
import {
  OAUTH_SCOPES,
  OAuthClientDraft,
  OAuthScopeName,
  draftOf,
  draftProblems,
  emptyDraft,
  suggestClientId,
  toCreate,
  toUpdate,
} from '../oauth.util';

type DialogMode = { kind: 'create' } | { kind: 'edit'; clientId: string };

/**
 * Region names by id: from the clients' `requiredRegions` (the server sends id + name, no lookup per id), then the
 * regions picked in the dialog (later sources win).
 */
export function regionNameMap(...sources: (readonly RegionRef[] | undefined)[]): ReadonlyMap<string, string> {
  const names = new Map<string, string>();
  sources.forEach((regions) => regions?.forEach((region) => names.set(region.id, region.name)));
  return names;
}

/**
 * `/admin/oauth-clients` (C13, ORGA, A56): applications that may sign users in through foodsharing. List with kind,
 * state, scopes, redirect URIs, region restriction and consent count; create/edit dialog (redirect-URI list editor,
 * scopes, refresh tokens, required regions through `fs-region-picker`, enabled); the secret of a confidential client
 * is shown once after creating or rotating it; enable/disable and delete (revokes consents and tokens) after confirm.
 */
@Component({
  selector: 'fs-oauth-clients-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    ClientSecretComponent,
    DateTimePipe,
    Dialog,
    FormsModule,
    InputText,
    NgTemplateOutlet,
    OAuthScopeListComponent,
    PageHeaderComponent,
    RadioButton,
    RedirectUrisEditorComponent,
    RegionPickerComponent,
    ToggleSwitch,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'oauth.clients.title' | t" [subtitle]="'oauth.clients.subtitle' | t" icon="pi pi-key">
      <p-button [label]="'oauth.clients.create' | t" icon="pi pi-plus" (onClick)="openCreate()" data-testid="oauth.clients.create" />
    </fs-page-header>

    <fs-data-state
      [state]="clients"
      skeleton="list"
      [skeletonRows]="3"
      emptyIcon="pi pi-key"
      [emptyTitle]="'oauth.clients.empty' | t"
      [emptyText]="'oauth.clients.emptyText' | t"
      testId="oauth.clients.state"
      data-testid="oauth.clients.state"
    >
      <ng-template [fsData]="clients" let-list>
        <ul class="clients" data-testid="oauth.clients.list">
          @for (client of list; track client.clientId) {
            <li
              class="fs-card client"
              [class.client--off]="!client.enabled"
              data-testid="oauth.clients.row"
              [attr.data-client]="client.clientId"
              [attr.data-enabled]="client.enabled"
            >
              <div class="client__head">
                <div class="client__title">
                  <h2 data-testid="oauth.clients.rowName">{{ client.name }}</h2>
                  <code class="muted">{{ client.clientId }}</code>
                </div>
                <div class="chips">
                  <span class="chip" data-testid="oauth.clients.rowKind">
                    {{ (client.confidential ? 'oauth.clients.kind.confidential' : 'oauth.clients.kind.public') | t }}
                  </span>
                  <span class="chip" [class.chip--on]="client.enabled" [class.chip--off]="!client.enabled" data-testid="oauth.clients.rowState">
                    {{ (client.enabled ? 'oauth.clients.enabled' : 'oauth.clients.disabled') | t }}
                  </span>
                </div>
              </div>
              <fs-oauth-scope-list [scopes]="client.scopes" [compact]="true" testId="oauth.clients.rowScopes" />
              <dl class="facts">
                <div>
                  <dt>{{ 'oauth.clients.redirectUris' | t }}</dt>
                  <dd>
                    @for (uri of client.redirectUris; track uri) {
                      <code class="uri">{{ uri }}</code>
                    }
                  </dd>
                </div>
                <div>
                  <dt>{{ 'oauth.clients.regions' | t }}</dt>
                  <dd data-testid="oauth.clients.rowRegions">
                    @for (id of client.requiredRegionIds; track id; let last = $last) {
                      {{ regionName(id) }}{{ last ? '' : ', ' }}
                    } @empty {
                      {{ 'oauth.clients.allRegions' | t }}
                    }
                  </dd>
                </div>
                <div>
                  <dt>{{ 'oauth.clients.consents' | t }}</dt>
                  <dd data-testid="oauth.clients.rowConsents">{{ client.consentCount }}</dd>
                </div>
                <div>
                  <dt>{{ 'oauth.clients.created' | t }}</dt>
                  <dd>{{ client.createdAt | fsDate: 'date' }}</dd>
                </div>
              </dl>
              <div class="client__actions">
                <p-button
                  [label]="'oauth.clients.edit' | t"
                  icon="pi pi-pencil"
                  severity="secondary"
                  [outlined]="true"
                  size="small"
                  (onClick)="openEdit(client)"
                  data-testid="oauth.clients.edit"
                />
                <p-button
                  [label]="(client.enabled ? 'oauth.clients.disable' : 'oauth.clients.enable') | t"
                  [icon]="client.enabled ? 'pi pi-pause' : 'pi pi-play'"
                  severity="secondary"
                  [text]="true"
                  size="small"
                  [loading]="toggling() === client.clientId"
                  (onClick)="confirmToggle(client)"
                  data-testid="oauth.clients.toggle"
                />
                @if (client.confidential) {
                  <p-button
                    [label]="'oauth.clients.rotate' | t"
                    icon="pi pi-refresh"
                    severity="secondary"
                    [text]="true"
                    size="small"
                    (onClick)="confirmRotate(client)"
                    data-testid="oauth.clients.rotate"
                  />
                }
                <p-button
                  [label]="'oauth.clients.delete' | t"
                  icon="pi pi-trash"
                  severity="danger"
                  [text]="true"
                  size="small"
                  (onClick)="confirmDelete(client)"
                  data-testid="oauth.clients.delete"
                />
              </div>
            </li>
          }
        </ul>
      </ng-template>
    </fs-data-state>

    @if (dialog(); as mode) {
      <p-dialog [closeAriaLabel]="'common.close' | t"
        [visible]="true"
        (visibleChange)="!$event && closeDialog()"
        [modal]="true"
        [draggable]="false"
        [header]="(mode.kind === 'create' ? 'oauth.clients.form.createTitle' : 'oauth.clients.form.editTitle') | t"
        [style]="{ width: 'min(42rem, 96vw)' }"
        data-testid="oauth.clients.dialog"
      >
        @if (mode.kind === 'edit') {
          <fs-data-state [state]="editClient" skeleton="form" [compact]="true" testId="oauth.clients.form.state" data-testid="oauth.clients.form.state">
            <ng-template [fsData]="editClient">
              <ng-container [ngTemplateOutlet]="form" />
            </ng-template>
          </fs-data-state>
        } @else {
          <ng-container [ngTemplateOutlet]="form" />
        }
      </p-dialog>
    }

    <ng-template #form>
      <form class="form" ngNoForm (submit)="$event.preventDefault(); save()" novalidate data-testid="oauth.clients.form">
        <div class="field">
          <label for="oauth-name">{{ 'oauth.clients.form.name' | t }}</label>
          <input
            pInputText
            id="oauth-name"
            maxlength="200"
            [value]="draft().name"
            (input)="setName($any($event.target).value)"
            [attr.aria-invalid]="shown('nameRequired')"
            data-testid="oauth.clients.form.name"
          />
          @if (shown('nameRequired')) {
            <p class="field-error" data-testid="oauth.clients.form.nameError">{{ 'oauth.clients.form.nameRequired' | t }}</p>
          }
        </div>

        @if (creating()) {
          <div class="field">
            <label for="oauth-client-id">{{ 'oauth.clients.clientId' | t }}</label>
            <input
              pInputText
              id="oauth-client-id"
              maxlength="60"
              autocapitalize="off"
              spellcheck="false"
              [value]="draft().clientId"
              (input)="setClientId($any($event.target).value)"
              [attr.aria-invalid]="shown('clientIdInvalid')"
              aria-describedby="oauth-client-id-hint"
              data-testid="oauth.clients.form.clientId"
            />
            <span class="muted" id="oauth-client-id-hint">{{ 'oauth.clients.form.clientIdHint' | t }}</span>
            @if (shown('clientIdInvalid')) {
              <p class="field-error" data-testid="oauth.clients.form.clientIdError">{{ 'oauth.clients.form.clientIdInvalid' | t }}</p>
            }
          </div>

          <fieldset class="field group">
            <legend class="label">{{ 'oauth.clients.form.kind' | t }}</legend>
            @for (kind of kinds; track kind.value) {
              <label class="option" [for]="'oauth-kind-' + kind.id">
                <p-radiobutton
                  name="oauth-kind"
                  [inputId]="'oauth-kind-' + kind.id"
                  [value]="kind.value"
                  [ngModel]="draft().confidential"
                  (ngModelChange)="patch({ confidential: $event })"
                  [attr.data-testid]="'oauth.clients.form.kind.' + kind.id"
                />
                <span>
                  <span class="option__title">{{ 'oauth.clients.kind.' + kind.id | t }}</span>
                  <span class="muted option__text">{{ 'oauth.clients.kindHint.' + kind.id | t }}</span>
                </span>
              </label>
            }
          </fieldset>
        } @else {
          <p class="muted" data-testid="oauth.clients.form.fixed">
            <code>{{ draft().clientId }}</code> ·
            {{ (draft().confidential ? 'oauth.clients.kind.confidential' : 'oauth.clients.kind.public') | t }}
          </p>
        }

        <fs-oauth-redirect-uris-editor [uris]="draft().redirectUris" (urisChange)="patch({ redirectUris: $event })" />
        @if (shown('redirectRequired') || shown('redirectInvalid') || shown('redirectTooMany')) {
          <p class="field-error" data-testid="oauth.clients.form.redirectError">
            {{ 'oauth.clients.form.' + firstRedirectProblem() | t }}
          </p>
        }

        <fieldset class="field group">
          <legend class="label">{{ 'oauth.clients.form.scopes' | t }}</legend>
          @for (scope of scopes; track scope) {
            <label class="option" [for]="'oauth-scope-' + scope">
              <p-checkbox
                [inputId]="'oauth-scope-' + scope"
                [binary]="true"
                [disabled]="scope === 'openid'"
                [ngModel]="draft().scopes.includes(scope)"
                (ngModelChange)="toggleScope(scope, $event)"
                [attr.data-testid]="'oauth.clients.form.scope.' + scope"
              />
              <span>
                <span class="option__title">{{ 'oauth.scope.' + scope + '.title' | t }}</span>
                <span class="muted option__text">{{ 'oauth.scope.' + scope + '.text' | t }}</span>
              </span>
            </label>
          }
        </fieldset>

        <fieldset class="field group">
          <legend class="label">{{ 'oauth.clients.form.grants' | t }}</legend>
          <label class="option" for="oauth-grant-code">
            <p-checkbox inputId="oauth-grant-code" [binary]="true" [disabled]="true" [ngModel]="true" />
            <span class="option__title">{{ 'oauth.clients.form.grantCode' | t }}</span>
          </label>
          <label class="option" for="oauth-grant-refresh">
            <p-checkbox
              inputId="oauth-grant-refresh"
              [binary]="true"
              [ngModel]="draft().refreshTokens"
              (ngModelChange)="patch({ refreshTokens: $event })"
              data-testid="oauth.clients.form.refreshTokens"
            />
            <span>
              <span class="option__title">{{ 'oauth.clients.form.grantRefresh' | t }}</span>
              <span class="muted option__text">{{ 'oauth.clients.form.grantRefreshHint' | t }}</span>
            </span>
          </label>
        </fieldset>

        <fieldset class="field group" data-testid="oauth.clients.form.regions">
          <legend class="label">{{ 'oauth.clients.form.regions' | t }}</legend>
          <p class="muted">{{ 'oauth.clients.form.regionsHint' | t }}</p>
          @if (draft().requiredRegionIds.length) {
            <ul class="region-chips">
              @for (id of draft().requiredRegionIds; track id) {
                <li class="chip region-chip" data-testid="oauth.clients.form.region" [attr.data-region]="id">
                  <span>{{ regionName(id) }}</span>
                  <button
                    type="button"
                    class="chip__remove"
                    [attr.aria-label]="'oauth.clients.form.removeRegion' | t: { name: regionName(id) }"
                    (click)="removeRegion(id)"
                    data-testid="oauth.clients.form.regionRemove"
                  >
                    <i class="pi pi-times" aria-hidden="true"></i>
                  </button>
                </li>
              }
            </ul>
          }
          @if (pickingRegion()) {
            <fs-region-picker initialMode="tree" (regionSelected)="addRegion($event)" testId="oauth.clients.form.regionPicker" />
            <div>
              <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" size="small" (onClick)="pickingRegion.set(false)" />
            </div>
          } @else {
            <div>
              <p-button
                [label]="'oauth.clients.form.addRegion' | t"
                icon="pi pi-map-marker"
                severity="secondary"
                [text]="true"
                size="small"
                (onClick)="pickingRegion.set(true)"
                data-testid="oauth.clients.form.addRegion"
              />
            </div>
          }
        </fieldset>

        <label class="option" for="oauth-enabled">
          <p-toggleswitch inputId="oauth-enabled" [ngModel]="draft().enabled" (ngModelChange)="patch({ enabled: $event })" data-testid="oauth.clients.form.enabled" />
          <span class="option__title">{{ 'oauth.clients.form.enabled' | t }}</span>
        </label>

        @if (serverError(); as error) {
          <p class="field-error" role="alert" data-testid="oauth.clients.form.error">{{ error }}</p>
        }

        <div class="dialog-actions">
          <p-button [label]="'common.cancel' | t" severity="secondary" [text]="true" (onClick)="closeDialog()" />
          <p-button
            type="submit"
            [label]="(creating() ? 'oauth.clients.form.createSubmit' : 'common.save') | t"
            icon="pi pi-check"
            [loading]="create.busy() || update.busy()"
            data-testid="oauth.clients.form.save"
          />
        </div>
      </form>
    </ng-template>

    @if (secret(); as shown) {
      <p-dialog [closeAriaLabel]="'common.close' | t"
        [visible]="true"
        (visibleChange)="!$event && secret.set(null)"
        [modal]="true"
        [draggable]="false"
        [header]="'oauth.clients.secret.title' | t"
        [style]="{ width: 'min(32rem, 94vw)' }"
        data-testid="oauth.clients.secretDialog"
      >
        <fs-oauth-client-secret [clientId]="shown.clientId" [secret]="shown.secret" />
        <div class="dialog-actions">
          <p-button [label]="'oauth.clients.secret.done' | t" icon="pi pi-check" (onClick)="secret.set(null)" data-testid="oauth.clients.secret.done" />
        </div>
      </p-dialog>
    }
  `,
  styleUrl: '../oauth.scss',
})
export class OAuthClientsPageComponent {
  private readonly api = inject(AdminOauthService);

  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly scopes = OAUTH_SCOPES;
  protected readonly kinds = [
    { id: 'confidential', value: true },
    { id: 'public', value: false },
  ] as const;

  protected readonly clients = loadResource<OAuthClient[]>({ loader: () => this.api.listOAuthClients() });

  protected readonly dialog = signal<DialogMode | null>(null);
  protected readonly creating = computed(() => this.dialog()?.kind === 'create');
  protected readonly draft = signal<OAuthClientDraft>(emptyDraft());
  protected readonly submitted = signal(false);
  protected readonly pickingRegion = signal(false);
  private readonly clientIdTouched = signal(false);
  protected readonly secret = signal<{ clientId: string; secret: string } | null>(null);
  protected readonly toggling = signal<string | null>(null);

  protected readonly problems = computed(() => draftProblems(this.draft(), this.creating()));
  protected readonly firstRedirectProblem = computed(
    () => this.problems().find((problem) => problem.startsWith('redirect')) ?? 'redirectInvalid',
  );

  /** The client being edited, fresh from the server. */
  protected readonly editClient = loadResource<OAuthClient, string>({
    params: () => {
      const mode = this.dialog();
      return mode?.kind === 'edit' ? mode.clientId : undefined;
    },
    loader: (clientId) => this.api.getOAuthClient({ clientId }),
  });

  /** Regions picked in the dialog (their names are not in any loaded client yet). */
  private readonly pickedRegions = signal<readonly RegionRef[]>([]);
  private readonly regionNames = computed(() =>
    regionNameMap(
      (this.clients.data() ?? []).flatMap((client) => client.requiredRegions),
      this.editClient.data()?.requiredRegions,
      this.pickedRegions(),
    ),
  );

  protected readonly create = runMutation(() => this.api.createOAuthClient({ oAuthClientCreate: toCreate(this.draft()) }), {
    errorToast: false,
    successToastKey: 'oauth.clients.createdToast',
    onSuccess: (created) => {
      this.closeDialog();
      this.clients.update((list) => [...(list ?? []), created.client]);
      if (created.clientSecret) {
        this.secret.set({ clientId: created.client.clientId, secret: created.clientSecret });
      }
    },
  });

  protected readonly update = runMutation(
    () => {
      const mode = this.dialog();
      const clientId = mode?.kind === 'edit' ? mode.clientId : '';
      return this.api.updateOAuthClient({ clientId, oAuthClientUpdate: toUpdate(this.draft()) });
    },
    {
      errorToast: false,
      successToastKey: 'oauth.clients.saved',
      onSuccess: (saved) => {
        this.closeDialog();
        this.replace(saved);
      },
    },
  );

  protected readonly serverError = computed(() => {
    const error = this.creating() ? this.create.error() : this.update.error();
    return error ? this.i18n.error(error.code) : undefined;
  });

  protected readonly setEnabled = runMutation(
    (client: OAuthClient, enabled: boolean) =>
      this.api.updateOAuthClient({ clientId: client.clientId, oAuthClientUpdate: { enabled } }),
    {
      onSuccess: (saved) => {
        this.toggling.set(null);
        this.replace(saved);
      },
      onError: () => this.toggling.set(null),
    },
  );

  protected readonly rotate = runMutation((client: OAuthClient) => this.api.rotateOAuthClientSecret({ clientId: client.clientId }), {
    onSuccess: ({ clientSecret }) => {
      const clientId = this.rotating;
      if (clientId) {
        this.secret.set({ clientId, secret: clientSecret });
      }
    },
  });
  private rotating: string | null = null;

  protected readonly remove = runMutation((client: OAuthClient) => this.api.deleteOAuthClient({ clientId: client.clientId }), {
    successToastKey: 'oauth.clients.deleted',
    onSuccess: () => this.clients.reload(),
  });

  constructor() {
    // the edit form starts from the freshly loaded client
    effect(() => {
      const client = this.editClient.data();
      if (client && this.dialog()?.kind === 'edit') {
        untracked(() => this.draft.set(draftOf(client)));
      }
    });
  }

  protected regionName(id: string): string {
    // a deleted region is left out of requiredRegions: its id stays readable
    return this.regionNames().get(id) ?? id;
  }

  protected shown(problem: string): boolean {
    return this.submitted() && this.problems().includes(problem);
  }

  protected openCreate(): void {
    this.reset(emptyDraft());
    this.dialog.set({ kind: 'create' });
  }

  protected openEdit(client: OAuthClient): void {
    this.reset(draftOf(client));
    this.dialog.set({ kind: 'edit', clientId: client.clientId });
  }

  protected closeDialog(): void {
    this.dialog.set(null);
    this.pickingRegion.set(false);
  }

  protected patch(changes: Partial<OAuthClientDraft>): void {
    this.draft.update((draft) => ({ ...draft, ...changes }));
  }

  protected setName(name: string): void {
    this.patch(this.creating() && !this.clientIdTouched() ? { name, clientId: suggestClientId(name) } : { name });
  }

  protected setClientId(clientId: string): void {
    this.clientIdTouched.set(true);
    this.patch({ clientId });
  }

  protected toggleScope(scope: OAuthScopeName, on: boolean): void {
    const scopes = this.draft().scopes.filter((item) => item !== scope);
    this.patch({ scopes: OAUTH_SCOPES.filter((item) => item === 'openid' || (on ? [...scopes, scope] : scopes).includes(item)) });
  }

  protected addRegion(region: RegionRef): void {
    this.pickedRegions.update((regions) => [...regions, region]);
    const ids = this.draft().requiredRegionIds;
    if (!ids.includes(region.id)) {
      this.patch({ requiredRegionIds: [...ids, region.id] });
    }
    this.pickingRegion.set(false);
  }

  protected removeRegion(id: string): void {
    this.patch({ requiredRegionIds: this.draft().requiredRegionIds.filter((item) => item !== id) });
  }

  protected save(): void {
    this.submitted.set(true);
    if (this.problems().length) {
      return;
    }
    void (this.creating() ? this.create.run() : this.update.run());
  }

  protected confirmToggle(client: OAuthClient): void {
    if (!client.enabled) {
      this.toggling.set(client.clientId);
      void this.setEnabled.run(client, true);
      return;
    }
    this.confirm.confirm({
      header: this.i18n.t('oauth.clients.disableTitle'),
      message: this.i18n.t('oauth.clients.disableText', { name: client.name }),
      icon: 'pi pi-pause',
      acceptLabel: this.i18n.t('oauth.clients.disable'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => {
        this.toggling.set(client.clientId);
        void this.setEnabled.run(client, false);
      },
    });
  }

  protected confirmRotate(client: OAuthClient): void {
    this.confirm.confirm({
      header: this.i18n.t('oauth.clients.rotateTitle'),
      message: this.i18n.t('oauth.clients.rotateText', { name: client.name }),
      icon: 'pi pi-refresh',
      acceptLabel: this.i18n.t('oauth.clients.rotate'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => {
        this.rotating = client.clientId;
        void this.rotate.run(client);
      },
    });
  }

  protected confirmDelete(client: OAuthClient): void {
    this.confirm.confirm({
      header: this.i18n.t('oauth.clients.deleteTitle'),
      message: this.i18n.t('oauth.clients.deleteText', { name: client.name, count: client.consentCount }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('oauth.clients.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(client),
    });
  }

  private reset(draft: OAuthClientDraft): void {
    this.draft.set(draft);
    this.submitted.set(false);
    this.clientIdTouched.set(false);
    this.pickingRegion.set(false);
  }

  private replace(saved: OAuthClient): void {
    this.clients.update((list) => (list ?? []).map((item) => (item.clientId === saved.clientId ? saved : item)));
  }
}
