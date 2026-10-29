import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Dialog } from '@openng/optimus-ui/dialog';
import { Popover } from '@openng/optimus-ui/popover';
import { Textarea } from '@openng/optimus-ui/textarea';

import { ForumPost, ForumReaction, ForumService, ReactionKey } from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { MarkdownComponent } from '../../../shared/markdown/markdown.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { REACTION_EMOJI, REACTION_KEYS, renderMentions } from '../forum.resource';

/**
 * One forum post (F3, G3): author chip, Markdown body (mentions as bold names), "edited" marker, reactions with the
 * list of who reacted, reply-with-quote, edit/delete for the author (delete also ORGA), hide with reason / unhide for
 * moderators. Hidden posts are collapsed for moderators and the author and a placeholder for everybody else.
 */
@Component({
  selector: 'fs-forum-post',
  imports: [Button, DateTimePipe, Dialog, FormsModule, MarkdownComponent, Popover, Textarea, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[attr.id]': "'post-' + post().id", class: 'fs-card', '[class.hidden]': '!!post().hidden' },
  template: `
    @let p = post();
    <header class="head">
      <fs-user-chip [name]="p.author.displayName" [userId]="p.author.id" [avatarUrl]="p.author.avatarUrl" size="md" />
      <span class="meta">
        <time [attr.datetime]="p.createdAt">{{ p.createdAt | fsDate: 'dateTime' }}</time>
        @if (p.editedAt) {
          <span data-testid="forum.post.edited">· {{ 'forum.post.edited' | t: { date: (p.editedAt | fsDate: 'dateTime') } }}</span>
        }
      </span>
    </header>

    @if (p.hidden; as h) {
      <p class="hidden-note" data-testid="forum.post.hiddenNote">
        <i class="pi pi-eye-slash" aria-hidden="true"></i>
        {{ (h.reason ? 'forum.post.hiddenWithReason' : 'forum.post.hiddenBy') | t: { reason: h.reason ?? '', name: h.by?.displayName ?? '' } }}
        @if (p.body) {
          <p-button [label]="(showHidden() ? 'forum.post.collapse' : 'forum.post.showHidden') | t" [text]="true" size="small" (onClick)="showHidden.set(!showHidden())" data-testid="forum.post.toggleHidden" />
        }
      </p>
    }

    @if (editing()) {
      <form class="edit" (ngSubmit)="save.run()">
        <label class="fs-visually-hidden" [for]="'edit-' + p.id">{{ 'forum.post.edit' | t }}</label>
        <textarea pTextarea [id]="'edit-' + p.id" name="body" rows="6" [(ngModel)]="draft" data-testid="forum.post.editBody"></textarea>
        <div class="row">
          <p-button [label]="'common.cancel' | t" [text]="true" (onClick)="editing.set(false)" />
          <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [loading]="save.busy()" data-testid="forum.post.save" />
        </div>
      </form>
    } @else if (p.body && (!p.hidden || showHidden())) {
      <fs-markdown class="body" [markdown]="rendered()" data-testid="forum.post.body" />
    }

    <footer class="foot">
      <div class="reactions" data-testid="forum.post.reactions">
        @for (r of p.reactions; track r.key) {
          <button
            type="button"
            class="reaction"
            [class.reaction--mine]="mine(r)"
            [attr.aria-pressed]="mine(r)"
            [attr.aria-label]="reactionLabel(r)"
            (click)="react.run(r.key, !mine(r))"
            data-testid="forum.post.reaction"
            [attr.data-key]="r.key"
          >
            <span aria-hidden="true">{{ emoji[r.key] }}</span> {{ r.users.length }}
          </button>
        }
        @if (mayReact()) {
          <p-button icon="pi pi-face-smile" [text]="true" [rounded]="true" [ariaLabel]="'forum.post.addReaction' | t" (onClick)="picker.toggle($event)" data-testid="forum.post.addReaction" />
        }
        @if (p.reactions.length) {
          <p-button [label]="'forum.post.whoReacted' | t" [text]="true" size="small" (onClick)="who.toggle($event)" data-testid="forum.post.whoReacted" />
        }
      </div>
      <div class="actions">
        @if (mayReact()) {
          <p-button icon="pi pi-reply" [label]="'forum.post.quote' | t" [text]="true" size="small" (onClick)="quote.emit(p)" data-testid="forum.post.quote" />
        }
        @if (p.mayEdit && !editing()) {
          <p-button icon="pi pi-pencil" [text]="true" [ariaLabel]="'forum.post.edit' | t" (onClick)="startEdit()" data-testid="forum.post.edit" />
        }
        @if (p.mayHide && !p.hidden) {
          <p-button icon="pi pi-eye-slash" [text]="true" [ariaLabel]="'forum.post.hide' | t" (onClick)="hideOpen.set(true)" data-testid="forum.post.hide" />
        }
        @if (p.hidden && moderator()) {
          <p-button icon="pi pi-eye" [label]="'forum.post.unhide' | t" [text]="true" size="small" [loading]="unhide.busy()" (onClick)="unhide.run()" data-testid="forum.post.unhide" />
        }
        @if (p.mayDelete) {
          <p-button icon="pi pi-trash" severity="danger" [text]="true" [ariaLabel]="'forum.post.delete' | t" (onClick)="confirmDelete()" data-testid="forum.post.delete" />
        }
      </div>
    </footer>

    <p-popover #picker>
      <div class="picker" role="group" [attr.aria-label]="'forum.post.addReaction' | t">
        @for (key of keys; track key) {
          <button type="button" class="pick" [attr.aria-label]="key" (click)="react.run(key, true); picker.hide()" data-testid="forum.post.pick" [attr.data-key]="key">{{ emoji[key] }}</button>
        }
      </div>
    </p-popover>
    <p-popover #who>
      <ul class="who" data-testid="forum.post.whoList">
        @for (r of p.reactions; track r.key) {
          <li><span aria-hidden="true">{{ emoji[r.key] }}</span> {{ names(r) }}</li>
        }
      </ul>
    </p-popover>

    @if (hideOpen()) {
      <p-dialog [visible]="true" (visibleChange)="hideOpen.set($event)" [modal]="true" [header]="'forum.post.hideTitle' | t" [closeAriaLabel]="'common.close' | t" [style]="{ width: 'min(28rem, calc(100vw - 2rem))' }">
        <form class="edit" (ngSubmit)="hide.run()">
          <label for="hide-reason">{{ 'forum.post.hideReason' | t }}</label>
          <textarea pTextarea id="hide-reason" name="reason" rows="3" maxlength="500" [(ngModel)]="reason" data-testid="forum.post.hideReason"></textarea>
          <div class="row">
            <p-button [label]="'common.cancel' | t" [text]="true" (onClick)="hideOpen.set(false)" />
            <p-button type="submit" severity="danger" [label]="'forum.post.hide' | t" [disabled]="!reason.trim()" [loading]="hide.busy()" data-testid="forum.post.hideConfirm" />
          </div>
        </form>
      </p-dialog>
    }
  `,
  styles: `
    :host { display: grid; gap: 0.5rem; padding: 0.85rem 1rem; scroll-margin-top: 5rem; }
    :host(.hidden) { border-style: dashed; }
    .head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.5rem; }
    .meta, .hidden-note { color: var(--fs-text-muted); font-size: 0.875rem; }
    .hidden-note { display: flex; flex-wrap: wrap; align-items: center; gap: 0.4rem; margin: 0; }
    .foot { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 0.25rem; }
    .reactions, .actions, .row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.25rem; }
    .row { justify-content: flex-end; }
    .reaction {
      min-height: 2rem; padding: 0.15rem 0.6rem; border: 1px solid var(--fs-border); border-radius: 999px;
      background: var(--fs-surface); color: var(--fs-text); font: inherit; cursor: pointer;
    }
    .reaction--mine { border-color: var(--fs-primary); background: var(--fs-primary-soft); }
    .picker { display: grid; grid-template-columns: repeat(6, 2.5rem); gap: 0.25rem; }
    .pick { width: 2.5rem; height: 2.5rem; border: 0; border-radius: var(--fs-radius-sm); background: transparent; font-size: 1.25rem; cursor: pointer; }
    .pick:hover, .pick:focus-visible { background: var(--fs-surface-muted); }
    .who { margin: 0; padding: 0; list-style: none; display: grid; gap: 0.3rem; max-width: 20rem; }
    .edit { display: grid; gap: 0.4rem; }
  `,
})
export class ForumPostComponent {
  private readonly api = inject(ForumService);
  private readonly session = inject(SessionService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  readonly post = input.required<ForumPost>();
  /** Thread moderators may unhide (A36). */
  readonly moderator = input(false);
  /** Reactions and quoting need write access to the thread. */
  readonly mayReact = input(true);
  readonly quote = output<ForumPost>();
  readonly changed = output<void>();

  protected readonly emoji = REACTION_EMOJI;
  protected readonly keys = REACTION_KEYS;
  protected readonly editing = signal(false);
  protected readonly showHidden = signal(false);
  protected readonly hideOpen = signal(false);
  protected draft = '';
  protected reason = '';

  protected readonly rendered = computed(() => renderMentions(this.post().body ?? ''));
  private readonly me = computed(() => this.session.user()?.id);

  protected mine(r: ForumReaction): boolean {
    return r.users.some((u) => u.id === this.me());
  }

  protected names(r: ForumReaction): string {
    return r.users.map((u) => u.displayName).join(', ');
  }

  protected reactionLabel(r: ForumReaction): string {
    return this.i18n.t('forum.post.reactionLabel', { emoji: r.key, count: r.users.length, names: this.names(r) });
  }

  protected startEdit(): void {
    this.draft = this.post().body ?? '';
    this.editing.set(true);
  }

  protected readonly react = runMutation(
    (key: ReactionKey, add: boolean) =>
      add ? this.api.addForumReaction({ postId: this.post().id, key }) : this.api.removeForumReaction({ postId: this.post().id, key }),
    { onSuccess: () => this.changed.emit() },
  );

  protected readonly save = runMutation(
    () => this.api.editForumPost({ postId: this.post().id, forumPostWrite: { body: this.draft.trim() } }),
    {
      successToastKey: 'forum.post.saved',
      onSuccess: () => {
        this.editing.set(false);
        this.changed.emit();
      },
    },
  );

  protected readonly hide = runMutation(
    () => this.api.hideForumPost({ postId: this.post().id, forumHideRequest: { reason: this.reason.trim() } }),
    {
      successToastKey: 'forum.post.hiddenToast',
      onSuccess: () => {
        this.hideOpen.set(false);
        this.reason = '';
        this.changed.emit();
      },
    },
  );

  protected readonly unhide = runMutation(() => this.api.unhideForumPost({ postId: this.post().id }), {
    successToastKey: 'forum.post.unhiddenToast',
    onSuccess: () => this.changed.emit(),
  });

  protected readonly remove = runMutation(() => this.api.deleteForumPost({ postId: this.post().id }), {
    successToastKey: 'forum.post.deleted',
    onSuccess: () => this.changed.emit(),
  });

  protected confirmDelete(): void {
    this.confirm.confirm({
      header: this.i18n.t('forum.post.deleteTitle'),
      message: this.i18n.t('forum.post.deleteText'),
      acceptLabel: this.i18n.t('forum.post.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(),
    });
  }
}
