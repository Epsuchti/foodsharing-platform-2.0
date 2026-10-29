import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';

import { UserSummary } from '../../api';
import { DataState } from '../../core/data/data-state';
import { DateTimePipe } from '../../core/dates/date-time.pipe';
import { I18nService } from '../../core/i18n/i18n.service';
import { TPipe } from '../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../data-state';
import { MarkdownComponent } from '../markdown/markdown.component';
import { UserChipComponent } from '../user-chip/user-chip.component';

let nextId = 0;

/** A post on a wall (regions, groups, food-share points, profiles; M6). `body` is Markdown. */
export interface WallPost {
  id: string;
  author?: UserSummary | null;
  body: string;
  createdAt: string;
  mayDelete: boolean;
}

/**
 * Shared wall (M6): optional composer (`mayPost`), posts newest first with author chip, Markdown body and relative
 * date (full date as tooltip), delete with confirmation. The host does the API calls: `post` emits the text (the
 * draft is cleared when `posts` changes afterwards, so a failed post keeps it), `delete` emits the confirmed post.
 * With `state` the list shows skeleton/empty/error/forbidden/offline via fs-data-state.
 *
 * `<fs-wall [posts]="items()" [state]="wall" [mayPost]="true" [posting]="post.busy()" (post)="post.run($event)" (delete)="remove.run($event)" />`
 */
@Component({
  selector: 'fs-wall',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    DateTimePipe,
    FormsModule,
    MarkdownComponent,
    NgTemplateOutlet,
    Textarea,
    TPipe,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'fs-wall', '[attr.data-testid]': 'testId()' },
  template: `
    @if (mayPost()) {
      <form class="fs-card composer" (ngSubmit)="submit()" [attr.data-testid]="testId() + '.composer'">
        <label class="composer__label" [for]="inputId">{{ 'shared.wall.composerLabel' | t }}</label>
        <textarea
          pTextarea
          [id]="inputId"
          name="body"
          rows="3"
          [maxlength]="maxLength()"
          [ngModel]="draft()"
          (ngModelChange)="draft.set($event)"
          [placeholder]="'shared.wall.placeholder' | t"
          [attr.data-testid]="testId() + '.text'"
        ></textarea>
        <div class="composer__foot">
          <small class="muted">{{ draft().length }} / {{ maxLength() }}</small>
          <p-button
            type="submit"
            [label]="'shared.wall.post' | t"
            icon="pi pi-send"
            size="small"
            [disabled]="!draft().trim()"
            [loading]="posting()"
            [attr.data-testid]="testId() + '.submit'"
          />
        </div>
      </form>
    }

    @if (state(); as wallState) {
      <fs-data-state
        [state]="wallState"
        skeleton="list"
        [emptyTitle]="'shared.wall.empty' | t"
        [emptyText]="(mayPost() ? 'shared.wall.emptyTextPost' : 'shared.wall.emptyText') | t"
        [testId]="testId() + '.state'"
        [attr.data-testid]="testId() + '.state'"
      >
        <ng-template [fsData]="wallState">
          <ng-container [ngTemplateOutlet]="list" />
        </ng-template>
      </fs-data-state>
    } @else if (posts().length === 0) {
      <p class="muted empty" [attr.data-testid]="testId() + '.empty'">{{ 'shared.wall.empty' | t }}</p>
    } @else {
      <ng-container [ngTemplateOutlet]="list" />
    }

    <ng-template #list>
      <ol class="posts" [attr.data-testid]="testId() + '.list'">
        @for (item of posts(); track item.id) {
          <li class="fs-card post" [attr.data-testid]="testId() + '.post'" [attr.data-post-id]="item.id">
            <div class="post__head">
              @if (item.author; as author) {
                <fs-user-chip
                  [name]="author.displayName"
                  [userId]="author.id"
                  [avatarUrl]="author.avatarUrl"
                  [sleepMode]="author.sleepMode"
                  [sleepMessage]="author.sleepMessage"
                />
              }
              <time class="muted" [attr.datetime]="item.createdAt" [title]="item.createdAt | fsDate: 'dateTime'">{{
                item.createdAt | fsDate: 'relative'
              }}</time>
            </div>
            <fs-markdown class="post__body" [markdown]="item.body" [attr.data-testid]="testId() + '.body'" />
            @if (item.mayDelete) {
              <p-button
                class="post__delete"
                icon="pi pi-trash"
                severity="danger"
                [text]="true"
                [rounded]="true"
                size="small"
                [ariaLabel]="'shared.wall.delete' | t"
                (onClick)="askDelete(item)"
                [attr.data-testid]="testId() + '.delete'"
              />
            }
          </li>
        }
      </ol>
    </ng-template>
  `,
  styles: `
    :host { display: grid; gap: 1rem; min-width: 0; }
    .composer { display: grid; gap: 0.5rem; padding: 1rem; }
    .composer__label { font-weight: 600; }
    .composer textarea { width: 100%; resize: vertical; }
    .composer__foot { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    .muted { color: var(--fs-text-muted); font-size: 0.85rem; }
    .empty { margin: 0; }
    .posts { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .post { position: relative; display: grid; gap: 0.4rem; padding: 0.9rem 3rem 0.9rem 1rem; min-width: 0; }
    .post__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.25rem 0.75rem; }
    .post__body { overflow-wrap: anywhere; }
    .post__delete { position: absolute; top: 0.5rem; right: 0.5rem; }
  `,
})
export class WallComponent {
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly posts = input<readonly WallPost[]>([]);
  /** Optional DataState of the posts (skeleton/empty/error/forbidden/offline). */
  readonly state = input<DataState<unknown> | undefined>(undefined);
  readonly mayPost = input(false);
  /** True while the host's post mutation runs. */
  readonly posting = input(false);
  readonly maxLength = input(4000);
  readonly testId = input('shared.wall');
  readonly post = output<string>();
  readonly delete = output<WallPost>();

  protected readonly inputId = `fs-wall-${nextId++}`;
  protected readonly draft = signal('');
  private submitted = false;

  constructor() {
    // a successful post changes `posts` (reload or prepend): only then the draft is cleared
    effect(() => {
      this.posts();
      untracked(() => {
        if (this.submitted && !this.posting()) {
          this.submitted = false;
          this.draft.set('');
        }
      });
    });
  }

  protected submit(): void {
    const body = this.draft().trim();
    if (!body || this.posting()) {
      return;
    }
    this.submitted = true;
    this.post.emit(body);
  }

  protected askDelete(post: WallPost): void {
    this.confirm.confirm({
      header: this.i18n.t('shared.wall.deleteTitle'),
      message: this.i18n.t('shared.wall.deleteText'),
      acceptLabel: this.i18n.t('shared.wall.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => this.delete.emit(post),
    });
  }
}
