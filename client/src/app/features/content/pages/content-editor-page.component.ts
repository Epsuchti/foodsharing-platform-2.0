import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputNumber } from '@openng/optimus-ui/inputnumber';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import {
  ContentPageKind,
  ContentPageSummary,
  ContentPageUpdate,
  ContentService,
  Role,
  Upload,
  WorkingGroupFunction,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { LANGUAGES } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import {
  LanguageTabsEditorComponent,
  TranslatedText,
} from '../../../shared/language-tabs-editor/language-tabs-editor.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { UploadDropzoneComponent } from '../../../shared/upload-dropzone/upload-dropzone.component';
import { MAX_IMAGES, contentImageUrls, imageMarkdown } from '../content-images';

const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const NEW = 'new';

/**
 * /admin/content/:slug (C1): edits a content page in five languages (title + Markdown body with preview, tabs mark
 * missing languages), kind, order, reading time and published flag. `/admin/content/new` creates a page.
 * Q11: the editor function (ORGA only, A54) and page images — uploaded through `fs-upload-dropzone`, claimed on save
 * (`imageUploadIds`, CONTENT_IMAGE, public) and inserted as Markdown into the open language tab of the body.
 */
@Component({
  selector: 'fs-content-editor-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    FormsModule,
    InputNumber,
    InputText,
    LanguageTabsEditorComponent,
    NgTemplateOutlet,
    PageHeaderComponent,
    RouterLink,
    Select,
    TPipe,
    ToggleSwitch,
    UploadDropzoneComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(isNew() ? 'content.editor.newTitle' : 'content.editor.title') | t"
      [subtitle]="isNew() ? undefined : slug()"
      icon="pi pi-pencil"
      backLink="/admin/content"
      [backLabel]="'content.editor.back' | t"
    >
      @if (!isNew()) {
        <p-button
          [label]="'content.editor.view' | t"
          icon="pi pi-eye"
          severity="secondary"
          [outlined]="true"
          [routerLink]="viewLink()"
          data-testid="content.editor.view"
        />
      }
    </fs-page-header>

    @if (isNew()) {
      <ng-container *ngTemplateOutlet="form" />
    } @else {
      <fs-data-state
        [state]="page"
        skeleton="form"
        backLink="/admin/content"
        [askWho]="'content.admin.askWho' | t"
        data-testid="content.editor.state"
      >
        <ng-template [fsData]="page">
          <ng-container *ngTemplateOutlet="form" />
        </ng-template>
      </fs-data-state>
    }

    <ng-template #form>
      <form class="editor" (ngSubmit)="save.run()" data-testid="content.editor.form">
        <section class="fs-card editor__texts">
          <fs-language-tabs-editor
            name="titles"
            [ngModel]="titles()"
            (ngModelChange)="titles.set($event)"
            [label]="'content.editor.titles' | t"
            testId="content.editor.titles"
          />
          <fs-language-tabs-editor
            #bodiesEditor
            name="bodies"
            [ngModel]="bodies()"
            (ngModelChange)="bodies.set($event)"
            [label]="'content.editor.bodies' | t"
            [multiline]="true"
            [markdown]="true"
            [rows]="14"
            testId="content.editor.bodies"
          />
          @if (missingBodies().length) {
            <p class="editor__warn" role="alert" data-testid="content.editor.missingBodies">
              <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
              {{ 'content.editor.missingBodies' | t: { languages: missingBodies().join(', ') } }}
            </p>
          }
          <div class="images" data-testid="content.editor.images">
            <span class="images__label">{{ 'content.editor.images' | t }}</span>
            <small>{{ 'content.editor.imagesHint' | t }}</small>
            <fs-upload-dropzone
              [(uploads)]="newImages"
              [maxFiles]="maxNewImages()"
              accept="image/*"
              testId="content.editor.imageUpload"
            />
            @if (imageUrls().length) {
              <ul class="images__list">
                @for (image of imageUrls(); track image.id) {
                  <li class="images__item" [attr.data-id]="image.id" data-testid="content.editor.image">
                    <img [src]="image.thumb" alt="" width="96" height="96" />
                    <p-button
                      [label]="'content.editor.insertImage' | t"
                      icon="pi pi-plus"
                      size="small"
                      severity="secondary"
                      [outlined]="true"
                      (onClick)="insertImage(image.url)"
                      data-testid="content.editor.insertImage"
                    />
                    <p-button
                      [label]="'content.editor.removeImage' | t"
                      icon="pi pi-trash"
                      size="small"
                      severity="danger"
                      [text]="true"
                      (onClick)="removeImage(image.id)"
                      data-testid="content.editor.removeImage"
                    />
                  </li>
                }
              </ul>
            }
            @if (removedImageIds().length) {
              <small role="status" data-testid="content.editor.imagesRemoved">{{
                'content.editor.imagesRemoved' | t: { count: removedImageIds().length }
              }}</small>
            }
          </div>
        </section>

        <aside class="fs-card editor__settings">
          @if (isNew()) {
            <div class="field">
              <label for="content-slug">{{ 'content.editor.slug' | t }}</label>
              <input
                pInputText
                id="content-slug"
                name="slug"
                maxlength="80"
                [ngModel]="newSlug()"
                (ngModelChange)="newSlug.set($event)"
                [attr.aria-invalid]="!!newSlug() && !slugValid()"
                data-testid="content.editor.slug"
              />
              <small [class.error]="!!newSlug() && !slugValid()">{{
                'content.editor.slugHint' | t
              }}</small>
            </div>
          }
          <div class="field">
            <label for="content-kind">{{ 'content.editor.kind' | t }}</label>
            <p-select
              inputId="content-kind"
              name="kind"
              [options]="kindOptions()"
              optionLabel="label"
              optionValue="value"
              [fluid]="true"
              [ngModel]="kind()"
              (ngModelChange)="kind.set($event)"
              data-testid="content.editor.kind"
            />
          </div>
          <div class="field">
            <label for="content-function">{{ 'content.editor.editorFunction' | t }}</label>
            <p-select
              inputId="content-function"
              name="editorFunction"
              [options]="functionOptions()"
              optionLabel="label"
              optionValue="value"
              [fluid]="true"
              [disabled]="!isOrga()"
              [ngModel]="editorFunction()"
              (ngModelChange)="editorFunction.set($event)"
              aria-describedby="content-function-hint"
              data-testid="content.editor.editorFunction"
            >
              <ng-template #item let-option
                ><span [attr.data-value]="option.value">{{ option.label }}</span></ng-template
              >
            </p-select>
            <small id="content-function-hint">{{
              (isOrga() ? 'content.editor.editorFunctionHint' : 'content.editor.editorFunctionOrga')
                | t
            }}</small>
          </div>
          <div class="field field--row">
            <div class="field">
              <label for="content-sort">{{ 'content.editor.sortOrder' | t }}</label>
              <p-inputnumber
                inputId="content-sort"
                name="sortOrder"
                [min]="0"
                [showButtons]="true"
                [fluid]="true"
                [ngModel]="sortOrder()"
                (ngModelChange)="sortOrder.set($event ?? 0)"
                data-testid="content.editor.sortOrder"
              />
            </div>
            @if (kind() === chapterKind) {
              <div class="field">
                <label for="content-minutes">{{ 'content.editor.minutes' | t }}</label>
                <p-inputnumber
                  inputId="content-minutes"
                  name="minutes"
                  [min]="0"
                  [max]="600"
                  [showButtons]="true"
                  [fluid]="true"
                  [ngModel]="minutes()"
                  (ngModelChange)="minutes.set($event)"
                  data-testid="content.editor.minutes"
                />
              </div>
            }
          </div>
          <div class="toggle">
            <p-toggleswitch
              inputId="content-published"
              name="published"
              [ngModel]="published()"
              (ngModelChange)="published.set($event)"
              data-testid="content.editor.published"
            />
            <label for="content-published">
              <strong>{{ 'content.editor.published' | t }}</strong>
              <span>{{ 'content.editor.publishedHint' | t }}</span>
            </label>
          </div>
          <div class="field">
            <label for="content-reason">{{ 'content.editor.reason' | t }}</label>
            <input
              pInputText
              id="content-reason"
              name="reason"
              maxlength="500"
              [ngModel]="reason()"
              (ngModelChange)="reason.set($event)"
              [placeholder]="'content.editor.reasonPlaceholder' | t"
              data-testid="content.editor.reason"
            />
          </div>
          <p-button
            type="submit"
            [label]="'content.editor.save' | t"
            icon="pi pi-save"
            styleClass="w-full"
            [disabled]="!valid()"
            [loading]="save.busy()"
            data-testid="content.editor.save"
          />
          @if (!valid()) {
            <small class="hint">{{ 'content.editor.saveHint' | t }}</small>
          }
        </aside>
      </form>
    </ng-template>
  `,
  styles: `
    :host {
      display: block;
    }
    .editor {
      display: grid;
      grid-template-columns: minmax(0, 1fr);
      gap: 1.25rem;
      align-items: start;
    }
    @media (min-width: 64rem) {
      .editor {
        grid-template-columns: minmax(0, 1fr) 20rem;
      }
      .editor__settings {
        position: sticky;
        top: calc(var(--fs-header-h, 4rem) + 1rem);
      }
    }
    .editor__texts,
    .editor__settings {
      display: grid;
      gap: 1.25rem;
      padding: clamp(1rem, 0.7rem + 1.2vw, 1.5rem);
    }
    .editor__warn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0;
      padding: 0.6rem 0.85rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
      font-size: 0.88rem;
    }
    .field {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .field label {
      font-weight: 600;
    }
    .field--row {
      grid-template-columns: repeat(auto-fit, minmax(7rem, 1fr));
      gap: 0.75rem;
    }
    .field small,
    .hint,
    .toggle span {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .field small.error {
      color: var(--fs-danger);
    }
    .field :is(input, p-select, p-inputnumber) {
      width: 100%;
    }
    .toggle {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
    }
    .toggle label {
      display: grid;
      gap: 0.1rem;
      cursor: pointer;
    }
    .images {
      display: grid;
      gap: 0.5rem;
    }
    .images__label {
      font-weight: 600;
    }
    .images small {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .images__list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .images__item {
      display: grid;
      justify-items: center;
      gap: 0.35rem;
    }
    .images__item img {
      width: 6rem;
      height: 6rem;
      object-fit: cover;
      border-radius: var(--fs-radius-sm);
      border: 1px solid var(--fs-border);
    }
  `,
})
export class ContentEditorPageComponent {
  private readonly api = inject(ContentService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);

  /** Route parameter (`new` creates a page). */
  readonly slug = input.required<string>();

  protected readonly isNew = computed(() => this.slug() === NEW);
  protected readonly chapterKind = ContentPageKind.LearningChapter;

  protected readonly titles = signal<TranslatedText>({});
  protected readonly bodies = signal<TranslatedText>({});
  protected readonly kind = signal<ContentPageKind>(ContentPageKind.Page);
  protected readonly sortOrder = signal(0);
  protected readonly minutes = signal<number | null>(null);
  protected readonly published = signal(false);
  protected readonly reason = signal('');
  protected readonly newSlug = signal('');
  /** Working-group function whose admins may edit the page (default PR; only ORGA changes it, A54). */
  protected readonly editorFunction = signal<string>(WorkingGroupFunction.Pr);
  /** Images already claimed by the page (ids) and the ones uploaded in this session. */
  protected readonly savedImageIds = signal<string[]>([]);
  protected readonly newImages = signal<Upload[]>([]);
  /** Claimed images removed on the next save (`removeImageUploadIds`, decision 117; their uploads are deleted). */
  protected readonly removedImageIds = signal<string[]>([]);

  private readonly bodiesEditor = viewChild<LanguageTabsEditorComponent>('bodiesEditor');

  protected readonly isOrga = computed(() => this.session.hasRole(Role.Orga));
  protected readonly functionOptions = computed(() => {
    this.i18n.language();
    return Object.values(WorkingGroupFunction).map((value) => ({
      value,
      label: this.i18n.t('content.function.' + value),
    }));
  });
  /** Claimed images that stay after the next save. */
  private readonly keptImageIds = computed(() =>
    this.savedImageIds().filter((id) => !this.removedImageIds().includes(id)),
  );
  protected readonly maxNewImages = computed(() =>
    Math.max(0, MAX_IMAGES - this.keptImageIds().length),
  );
  protected readonly imageUrls = computed(() =>
    [
      ...this.keptImageIds(),
      ...this.newImages()
        .map((upload) => upload.id)
        .filter((id) => !this.savedImageIds().includes(id)),
    ].map((id) => ({ id, ...contentImageUrls(id) })),
  );

  protected readonly page = loadResource<ContentPageUpdate, string | undefined>({
    params: () => (this.isNew() ? undefined : this.slug()),
    loader: (slug) => this.api.getContentPageForEdit({ slug: slug! }),
  });

  protected readonly kindOptions = computed(() => {
    this.i18n.language();
    return [ContentPageKind.Page, ContentPageKind.LearningChapter, ContentPageKind.Faq].map(
      (value) => ({
        value,
        label: this.i18n.t('content.kind.' + value),
      }),
    );
  });

  protected readonly slugValid = computed(() => SLUG_PATTERN.test(this.newSlug().trim()));
  /** Languages with a title but no body (the server refuses those). */
  protected readonly missingBodies = computed(() =>
    LANGUAGES.filter((lang) => this.titles()[lang]?.trim() && !this.bodies()[lang]?.trim()),
  );
  protected readonly valid = computed(
    () =>
      (!this.isNew() || this.slugValid()) &&
      !!this.titles().de?.trim() &&
      !!this.bodies().de?.trim() &&
      this.missingBodies().length === 0,
  );

  protected readonly viewLink = computed(() =>
    this.kind() === ContentPageKind.LearningChapter
      ? ['/learn', this.slug()]
      : ['/pages', this.slug()],
  );

  constructor() {
    effect(() => {
      const page = this.page.data();
      if (page && !this.isNew()) {
        this.titles.set({ ...page.titles });
        this.bodies.set({ ...page.bodies });
        this.kind.set(page.kind);
        this.sortOrder.set(page.sortOrder ?? 0);
        this.minutes.set(page.estimatedMinutes ?? null);
        this.published.set(page.published);
        this.reason.set('');
        this.editorFunction.set(page.editorFunction || WorkingGroupFunction.Pr);
        this.savedImageIds.set(page.imageUploadIds ?? []);
        this.newImages.set([]);
        this.removedImageIds.set([]);
      }
    });
  }

  /**
   * Removes an image: one uploaded in this session is only dropped (never claimed), a claimed one is removed on save.
   * Markdown in the bodies that links the image is left to the author.
   */
  protected removeImage(id: string): void {
    if (this.savedImageIds().includes(id)) {
      this.removedImageIds.update((ids) => (ids.includes(id) ? ids : [...ids, id]));
    } else {
      this.newImages.update((uploads) => uploads.filter((upload) => upload.id !== id));
    }
  }

  /** Adds the image's Markdown to the body of the language tab that is open. */
  protected insertImage(url: string): void {
    this.bodiesEditor()?.insert(imageMarkdown(url));
  }

  protected readonly save = runMutation(
    () => {
      const slug = this.isNew() ? this.newSlug().trim() : this.slug();
      return this.api.upsertContentPage({
        slug,
        contentPageUpdate: {
          titles: trimmed(this.titles()),
          bodies: trimmed(this.bodies()),
          kind: this.kind(),
          sortOrder: this.sortOrder(),
          estimatedMinutes:
            this.kind() === ContentPageKind.LearningChapter
              ? (this.minutes() ?? undefined)
              : undefined,
          published: this.published(),
          reason: this.reason().trim() || undefined,
          // only ORGA picks another function; others leave it unchanged (the server answers 403 otherwise)
          editorFunction: this.isOrga() ? this.editorFunction() : undefined,
          // new images are claimed and added; claimed ones are removed only through removeImageUploadIds
          imageUploadIds: this.newImages().length
            ? this.newImages().map((upload) => upload.id)
            : undefined,
          removeImageUploadIds: this.removedImageIds().length ? this.removedImageIds() : undefined,
        },
      });
    },
    {
      successToastKey: 'content.editor.saved',
      onSuccess: (saved: ContentPageSummary) => {
        this.reason.set('');
        if (this.isNew()) {
          void this.router.navigate(['/admin/content', saved.slug]);
        } else {
          this.page.reload();
        }
      },
    },
  );
}

function trimmed(texts: TranslatedText): Record<string, string> {
  return Object.fromEntries(
    Object.entries(texts)
      .map(([lang, text]) => [lang, (text ?? '').trim()])
      .filter(([, text]) => text),
  );
}
