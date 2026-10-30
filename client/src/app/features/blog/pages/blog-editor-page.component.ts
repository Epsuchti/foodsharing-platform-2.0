import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ConfirmationService, MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';

import { BlogPost, ContentService, Role, Upload, UploadsService } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { ImageCropComponent } from '../../../shared/image-crop/image-crop.component';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { blogStatus, blogUnits, isBlogEditor, teaserFromBody } from '../blog.util';
import { BlogStatusTagComponent } from '../components/blog-status-tag.component';

const NEW = 'new';
/** Select value of "no region" (a global post, editors only). */
const GLOBAL = '';

interface RegionOption {
  value: string;
  label: string;
}

/**
 * /blog/new and /blog/:id/edit (C2): title, teaser (taken from the body when empty), Markdown body with preview,
 * region and a picture (`fs-image-crop` → JPEG → `createUpload`, attached on save, A41). Editors (ORGA, PR group
 * admins) publish and unpublish; ambassadors write posts of the regions they administrate and ask for a check, which
 * bells the PR admins (`BLOG_NEW_CHECK`, link to this page). Changes are saved before publishing.
 */
@Component({
  selector: 'fs-blog-editor-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BlogStatusTagComponent,
    Button,
    FormsModule,
    ImageCropComponent,
    InputText,
    MarkdownComponent,
    NgTemplateOutlet,
    PageHeaderComponent,
    RouterLink,
    Select,
    Textarea,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [title]="(isNew() ? 'blog.editor.newTitle' : 'blog.editor.title') | t"
      icon="pi pi-pencil"
      backLink="/blog/manage"
      [backLabel]="'blog.editor.back' | t"
    >
      @if (current(); as p) {
        <fs-blog-status-tag
          [published]="p.published"
          [awaitingCheck]="p.awaitingCheck"
          testId="blog.editor.status"
        />
        @if (p.published) {
          <p-button
            [label]="'blog.editor.view' | t"
            icon="pi pi-eye"
            severity="secondary"
            [outlined]="true"
            [routerLink]="['/blog', p.id]"
            data-testid="blog.editor.view"
          />
        }
      }
    </fs-page-header>

    @if (isNew()) {
      <ng-container *ngTemplateOutlet="form" />
    } @else {
      <fs-data-state
        [state]="post"
        skeleton="form"
        backLink="/blog/manage"
        [askWho]="'blog.manage.askWho' | t"
        data-testid="blog.editor.state"
      >
        <ng-template [fsData]="post">
          <ng-container *ngTemplateOutlet="form" />
        </ng-template>
      </fs-data-state>
    }

    <ng-template #form>
      @if (readOnly()) {
        <p class="notice" role="status" data-testid="blog.editor.readOnly">
          <i class="pi pi-lock" aria-hidden="true"></i>{{ 'blog.editor.readOnly' | t }}
        </p>
      }
      <form class="editor" (ngSubmit)="save.run()" data-testid="blog.editor.form">
        <section class="fs-card editor__texts">
          <div class="field">
            <label for="blog-title">{{ 'blog.editor.postTitle' | t }}</label>
            <input
              pInputText
              id="blog-title"
              name="title"
              maxlength="200"
              required
              [disabled]="readOnly()"
              [ngModel]="title()"
              (ngModelChange)="change(title, $event)"
              data-testid="blog.editor.postTitle"
            />
          </div>
          <div class="field">
            <label for="blog-teaser">{{ 'blog.editor.teaser' | t }}</label>
            <textarea
              pTextarea
              id="blog-teaser"
              name="teaser"
              rows="3"
              maxlength="1000"
              [disabled]="readOnly()"
              [ngModel]="teaser()"
              (ngModelChange)="change(teaser, $event)"
              [placeholder]="teaserPlaceholder()"
              aria-describedby="blog-teaser-hint"
              data-testid="blog.editor.teaser"
            ></textarea>
            <small id="blog-teaser-hint">{{ 'blog.editor.teaserHint' | t }}</small>
          </div>
          <div class="field">
            <div class="field__head">
              <label for="blog-body">{{ 'blog.editor.body' | t }}</label>
              <p-button
                [label]="(preview() ? 'blog.editor.write' : 'blog.editor.preview') | t"
                [icon]="preview() ? 'pi pi-pencil' : 'pi pi-eye'"
                size="small"
                [text]="true"
                (onClick)="preview.set(!preview())"
                data-testid="blog.editor.previewToggle"
              />
            </div>
            @if (preview()) {
              <div class="preview" data-testid="blog.editor.preview">
                <fs-markdown [markdown]="body()" />
              </div>
            } @else {
              <textarea
                pTextarea
                id="blog-body"
                name="body"
                rows="16"
                maxlength="32600"
                required
                [disabled]="readOnly()"
                [ngModel]="body()"
                (ngModelChange)="change(body, $event)"
                aria-describedby="blog-body-hint"
                data-testid="blog.editor.body"
              ></textarea>
              <small id="blog-body-hint">{{ 'blog.editor.bodyHint' | t }}</small>
            }
          </div>
        </section>

        <aside class="fs-card editor__settings">
          <div class="field">
            <label for="blog-region">{{ 'blog.editor.region' | t }}</label>
            <p-select
              inputId="blog-region"
              name="region"
              [options]="regionOptions()"
              optionLabel="label"
              optionValue="value"
              [placeholder]="'blog.editor.regionPlaceholder' | t"
              [fluid]="true"
              [disabled]="readOnly()"
              [ngModel]="regionId()"
              (ngModelChange)="change(regionId, $event)"
              data-testid="blog.editor.region"
            >
              <ng-template #item let-option
                ><span [attr.data-value]="option.value || 'global'">{{ option.label }}</span></ng-template
              >
            </p-select>
            @if (!editor()) {
              <small>{{ 'blog.editor.regionHint' | t }}</small>
            }
          </div>

          <div class="field" data-testid="blog.editor.pictureSection">
            <span class="field__label">{{ 'blog.editor.picture' | t }}</span>
            @if (pictureSrc(); as src) {
              <img class="picture" [src]="src" alt="" width="320" height="180" data-testid="blog.editor.picturePreview" />
              @if (!readOnly()) {
                <p-button
                  [label]="'blog.editor.removePicture' | t"
                  icon="pi pi-trash"
                  severity="danger"
                  size="small"
                  [text]="true"
                  [disabled]="upload.busy()"
                  (onClick)="removePicture()"
                  data-testid="blog.editor.removePicture"
                />
              }
            } @else if (pictureRemoved()) {
              <small role="status" data-testid="blog.editor.pictureRemoved">{{
                'blog.editor.pictureRemoved' | t
              }}</small>
            }
            @if (!readOnly()) {
              <fs-image-crop
                [outputSize]="1280"
                testId="blog.editor.picture"
                (cropped)="uploadPicture($event)"
              />
            }
            @if (upload.busy()) {
              <small role="status" data-testid="blog.editor.pictureUploading">
                <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
                {{ 'blog.editor.pictureUploading' | t }}
              </small>
            }
          </div>

          @if (!readOnly()) {
            <p-button
              type="submit"
              [label]="'blog.editor.save' | t"
              icon="pi pi-save"
              styleClass="w-full"
              [disabled]="!valid() || upload.busy()"
              [loading]="save.busy()"
              data-testid="blog.editor.save"
            />
            @if (!valid()) {
              <small class="hint">{{ 'blog.editor.saveHint' | t }}</small>
            }

            @if (current(); as p) {
              <div class="actions">
                @if (editor()) {
                  @if (p.published) {
                    <p-button
                      [label]="'blog.editor.unpublish' | t"
                      icon="pi pi-eye-slash"
                      severity="secondary"
                      [outlined]="true"
                      styleClass="w-full"
                      [loading]="publish.busy()"
                      (onClick)="setPublished(false)"
                      data-testid="blog.editor.unpublish"
                    />
                  } @else {
                    <p-button
                      [label]="'blog.editor.publish' | t"
                      icon="pi pi-globe"
                      severity="success"
                      styleClass="w-full"
                      [disabled]="!valid()"
                      [loading]="publish.busy()"
                      (onClick)="setPublished(true)"
                      data-testid="blog.editor.publish"
                    />
                  }
                } @else if (p.awaitingCheck) {
                  <p-button
                    [label]="'blog.editor.withdraw' | t"
                    icon="pi pi-undo"
                    severity="secondary"
                    [outlined]="true"
                    styleClass="w-full"
                    [loading]="publish.busy()"
                    (onClick)="setPublished(false)"
                    data-testid="blog.editor.withdraw"
                  />
                  <small class="hint">{{ 'blog.editor.awaitingHint' | t }}</small>
                } @else if (!p.published) {
                  <p-button
                    [label]="'blog.editor.requestCheck' | t"
                    icon="pi pi-send"
                    styleClass="w-full"
                    [disabled]="!valid()"
                    [loading]="publish.busy()"
                    (onClick)="setPublished(true)"
                    data-testid="blog.editor.requestCheck"
                  />
                  <small class="hint">{{ 'blog.editor.requestCheckHint' | t }}</small>
                } @else {
                  <small class="hint">{{ 'blog.editor.publishedHint' | t }}</small>
                }
                <p-button
                  [label]="'blog.editor.delete' | t"
                  icon="pi pi-trash"
                  severity="danger"
                  [text]="true"
                  styleClass="w-full"
                  [loading]="remove.busy()"
                  (onClick)="confirmDelete(p)"
                  data-testid="blog.editor.delete"
                />
              </div>
            }
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
        grid-template-columns: minmax(0, 1fr) 21rem;
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
    .field {
      display: grid;
      gap: 0.35rem;
      min-width: 0;
    }
    .field label,
    .field__label {
      font-weight: 600;
    }
    .field__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }
    .field :is(input, textarea, p-select) {
      width: 100%;
    }
    .field small,
    .hint {
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .preview {
      min-height: 12rem;
      padding: 0.75rem 1rem;
      border: 1px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-sm);
    }
    .picture {
      width: 100%;
      height: auto;
      aspect-ratio: 16 / 9;
      object-fit: cover;
      border-radius: var(--fs-radius-sm);
    }
    .actions {
      display: grid;
      gap: 0.6rem;
      padding-top: 1rem;
      border-top: 1px solid var(--fs-border);
    }
    .notice {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0 0 1rem;
      padding: 0.6rem 0.85rem;
      border-radius: var(--fs-radius-md);
      background: var(--fs-warning-soft);
      color: var(--fs-warning-text);
    }
  `,
})
export class BlogEditorPageComponent {
  private readonly api = inject(ContentService);
  private readonly uploads = inject(UploadsService);
  private readonly session = inject(SessionService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly confirm = inject(ConfirmationService);
  private readonly messages = inject(MessageService);

  /** Route parameter (`new` on /blog/new). */
  readonly id = input<string>(NEW);

  protected readonly isNew = computed(() => this.id() === NEW);
  protected readonly editor = computed(() =>
    isBlogEditor(this.session.session(), this.session.hasRole(Role.Orga)),
  );

  protected readonly title = signal('');
  protected readonly teaser = signal('');
  protected readonly body = signal('');
  protected readonly regionId = signal<string | null>(null);
  protected readonly pictureUploadId = signal<string | null>(null);
  protected readonly localPicture = signal<string | null>(null);
  /** The saved picture is removed on the next save (`BlogPostUpdate.removePicture`, decision 117). */
  protected readonly pictureRemoved = signal(false);
  protected readonly preview = signal(false);
  private readonly dirty = signal(false);

  protected readonly post = loadResource<BlogPost, string | undefined>({
    params: () => (this.isNew() ? undefined : this.id()),
    loader: (blogPostId) => this.api.getBlogPost({ blogPostId: blogPostId! }),
  });

  /** The saved post (undefined while creating). */
  protected readonly current = computed(() => (this.isNew() ? undefined : this.post.data()));
  protected readonly readOnly = computed(() => this.current()?.mayEdit === false);

  protected readonly regionOptions = computed<RegionOption[]>(() => {
    this.i18n.language();
    const options: RegionOption[] = blogUnits(this.session.session()).map((unit) => ({
      value: unit.id,
      label: unit.name,
    }));
    const region = this.current()?.region;
    if (region && !options.some((option) => option.value === region.id)) {
      options.unshift({ value: region.id, label: region.name });
    }
    if (this.editor()) {
      options.unshift({ value: GLOBAL, label: this.i18n.t('blog.editor.global') });
    }
    return options;
  });

  protected readonly teaserPlaceholder = computed(() => teaserFromBody(this.body()));
  protected readonly pictureSrc = computed(
    () =>
      this.localPicture() ?? (this.pictureRemoved() ? null : (this.current()?.pictureUrl ?? null)),
  );
  protected readonly valid = computed(
    () =>
      !!this.title().trim() &&
      !!this.body().trim() &&
      (this.regionId() !== null || this.editor()),
  );

  constructor() {
    effect(() => {
      const post = this.current();
      if (post) {
        this.title.set(post.title);
        this.teaser.set(post.teaser ?? '');
        this.body.set(post.body);
        this.regionId.set(post.region?.id ?? (this.editor() ? GLOBAL : null));
        this.pictureUploadId.set(null);
        this.pictureRemoved.set(false);
        this.dirty.set(false);
      }
    });
    effect(() => {
      // A new post starts in the only region an ambassador administrates, or as a global post for editors.
      if (this.isNew() && this.regionId() === null) {
        const options = this.regionOptions();
        if (this.editor()) {
          this.regionId.set(GLOBAL);
        } else if (options.length === 1) {
          this.regionId.set(options[0].value);
        }
      }
    });
    inject(DestroyRef).onDestroy(() => this.revokePreview());
  }

  protected change<T>(target: { set(value: T): void }, value: T): void {
    target.set(value);
    this.dirty.set(true);
  }

  protected readonly upload = runMutation((file: File) => this.uploads.createUpload({ file }), {
    onSuccess: (upload: Upload) => {
      this.pictureUploadId.set(upload.id);
      this.dirty.set(true);
    },
  });

  protected uploadPicture(blob: Blob): void {
    this.revokePreview();
    this.localPicture.set(URL.createObjectURL(blob));
    this.pictureUploadId.set(null);
    // a new picture replaces the saved one; removePicture is never sent together with pictureUploadId
    this.pictureRemoved.set(false);
    void this.upload.run(new File([blob], 'blog.jpg', { type: 'image/jpeg' }));
  }

  /** Drops a picture uploaded in this session and marks the saved one (if any) for removal on save. */
  protected removePicture(): void {
    this.revokePreview();
    this.pictureUploadId.set(null);
    this.pictureRemoved.set(!!this.current()?.pictureUrl);
    this.dirty.set(true);
  }

  private request() {
    const region = this.regionId();
    return {
      title: this.title().trim(),
      teaser: this.teaser().trim() || teaserFromBody(this.body()),
      body: this.body().trim(),
      regionId: region ? region : undefined,
      pictureUploadId: this.pictureUploadId() ?? undefined,
    };
  }

  protected readonly save = runMutation(
    () =>
      this.isNew()
        ? this.api.createBlogPost({ blogPostCreate: this.request() })
        : this.api.updateBlogPost({
            blogPostId: this.id(),
            blogPostUpdate: {
              ...this.request(),
              removePicture: this.pictureRemoved() ? true : undefined,
            },
          }),
    {
      successToastKey: 'blog.editor.saved',
      onSuccess: (saved: BlogPost) => {
        this.dirty.set(false);
        if (this.isNew()) {
          void this.router.navigate(['/blog', saved.id, 'edit']);
        } else {
          this.revokePreview();
          this.post.set(saved);
        }
      },
    },
  );

  protected readonly publish = runMutation(
    (published: boolean) =>
      this.api.setBlogPostPublished({ blogPostId: this.id(), publishedUpdate: { published } }),
    {
      onSuccess: (saved: BlogPost) => {
        this.post.set(saved);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('blog.editor.toast.' + blogStatus(saved)),
          life: 4000,
        });
      },
    },
  );

  protected async setPublished(published: boolean): Promise<void> {
    if (published && this.dirty() && !(await this.save.run())) {
      return;
    }
    await this.publish.run(published);
  }

  protected readonly remove = runMutation(
    (post: BlogPost) => this.api.deleteBlogPost({ blogPostId: post.id }),
    {
      successToastKey: 'blog.editor.deleted',
      onSuccess: () => void this.router.navigate(['/blog/manage']),
    },
  );

  protected confirmDelete(post: BlogPost): void {
    this.confirm.confirm({
      header: this.i18n.t('blog.editor.deleteTitle'),
      message: this.i18n.t('blog.editor.deleteText', { title: post.title }),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('blog.editor.deleteConfirm'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonProps: { severity: 'danger' },
      rejectButtonProps: { severity: 'secondary', text: true },
      accept: () => void this.remove.run(post),
    });
  }

  private revokePreview(): void {
    const url = this.localPicture();
    if (url) {
      URL.revokeObjectURL(url);
      this.localPicture.set(null);
    }
  }
}
