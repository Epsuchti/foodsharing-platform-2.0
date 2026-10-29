import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, inject, input, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';
import { debounceTime, filter } from 'rxjs';

import { ForumPost, ForumService, ForumThread, ForumThreadUpdate, RealtimeEventType, ThreadStatus } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { ForumPostComponent } from '../components/forum-post.component';
import { quotePost } from '../forum.resource';

/**
 * /forum/threads/:threadId (F2–F4, G3): title (inline rename when permitted), status tags, moderation controls
 * (activate, close/reopen, sticky, delete), follow by bell and/or mail, posts and the reply composer. Refreshes on
 * realtime FORUM_POST events of this thread and on reconnect; bells deep-link to `#post-<id>`.
 */
@Component({
  selector: 'fs-thread-page',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, FormsModule, ForumPostComponent, InputText, PageHeaderComponent, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-data-state [state]="thread" skeleton="detail" data-testid="forum.thread.state">
      <ng-template [fsData]="thread" let-t>
        <fs-page-header [title]="t.title" icon="pi pi-comments" [backLink]="backLink(t)" [backLabel]="(t.ambassadorForum ? 'forum.ambassadors' : 'forum.title') | t">
          @if (t.permissions.mayRename && !renaming()) {
            <p-button icon="pi pi-pencil" [text]="true" [ariaLabel]="'forum.thread.rename' | t" (onClick)="startRename(t)" data-testid="forum.thread.rename" />
          }
        </fs-page-header>

        @if (renaming()) {
          <form class="rename fs-card" (ngSubmit)="update.run({ title: newTitle.trim() })">
            <label for="thread-rename">{{ 'forum.thread.rename' | t }}</label>
            <input pInputText id="thread-rename" name="title" maxlength="200" [(ngModel)]="newTitle" data-testid="forum.thread.renameInput" />
            <p-button [label]="'common.cancel' | t" [text]="true" (onClick)="renaming.set(false)" />
            <p-button type="submit" [label]="'common.save' | t" icon="pi pi-check" [loading]="update.busy()" data-testid="forum.thread.renameSave" />
          </form>
        }

        <div class="bar">
          <div class="tags" data-testid="forum.thread.tags">
            @if (t.sticky) {<span class="badge badge--primary" data-testid="forum.thread.sticky"><i class="pi pi-thumbtack" aria-hidden="true"></i> {{ 'forum.sticky' | t }}</span>}
            @if (t.status === 'CLOSED') {<span class="badge" data-testid="forum.thread.closed"><i class="pi pi-lock" aria-hidden="true"></i> {{ 'forum.closed' | t }}</span>}
            @if (!t.active) {<span class="badge badge--warn" data-testid="forum.thread.inactive">{{ 'forum.inactive' | t }}</span>}
          </div>
          <fieldset class="follow">
            <legend class="fs-visually-hidden">{{ 'forum.follow.legend' | t }}</legend>
            <span class="check"><p-checkbox [binary]="true" [ngModel]="t.following.bell" (ngModelChange)="follow.run({ bell: $event, mail: t.following.mail })" inputId="follow-bell" data-testid="forum.thread.followBell" /> <label for="follow-bell">{{ 'forum.follow.bell' | t }}</label></span>
            <span class="check"><p-checkbox [binary]="true" [ngModel]="t.following.mail" (ngModelChange)="follow.run({ bell: t.following.bell, mail: $event })" inputId="follow-mail" data-testid="forum.thread.followMail" /> <label for="follow-mail">{{ 'forum.follow.mail' | t }}</label></span>
          </fieldset>
        </div>

        @if (t.permissions.mayModerate || t.permissions.mayChangeSticky || t.permissions.mayDelete) {
          <div class="moderation fs-card" role="group" [attr.aria-label]="'forum.thread.moderation' | t" data-testid="forum.thread.moderation">
            @if (t.permissions.mayModerate && !t.active) {
              <p-button icon="pi pi-check-circle" [label]="'forum.thread.activate' | t" size="small" (onClick)="update.run({ active: true })" data-testid="forum.thread.activate" />
            }
            @if (t.permissions.mayModerate) {
              <p-button
                [icon]="t.status === 'OPEN' ? 'pi pi-lock' : 'pi pi-lock-open'"
                [label]="(t.status === 'OPEN' ? 'forum.thread.close' : 'forum.thread.reopen') | t"
                size="small"
                severity="secondary" [outlined]="true"
                (onClick)="toggleStatus(t)"
                data-testid="forum.thread.toggleStatus"
              />
            }
            @if (t.permissions.mayChangeSticky) {
              <p-button icon="pi pi-thumbtack" [label]="(t.sticky ? 'forum.thread.unstick' : 'forum.thread.stick') | t" size="small" severity="secondary" [outlined]="true" (onClick)="update.run({ sticky: !t.sticky })" data-testid="forum.thread.toggleSticky" />
            }
            @if (t.permissions.mayDelete) {
              <p-button icon="pi pi-trash" severity="danger" [label]="'forum.thread.delete' | t" size="small" severity="secondary" [outlined]="true" (onClick)="confirmDelete(t)" data-testid="forum.thread.delete" />
            }
          </div>
        }

        <div class="posts" data-testid="forum.thread.posts">
          @for (p of t.posts; track p.id) {
            <fs-forum-post [post]="p" [moderator]="t.permissions.mayModerate" [mayReact]="t.permissions.mayPost" (quote)="quoteInto($event)" (changed)="thread.reload()" data-testid="forum.thread.post" [attr.data-post-id]="p.id" />
          }
        </div>

        @if (t.permissions.mayPost) {
          <form class="composer fs-card" (ngSubmit)="reply.run()">
            <label for="forum-reply">{{ 'forum.thread.reply' | t }}</label>
            <textarea #composer pTextarea id="forum-reply" name="body" rows="5" [(ngModel)]="draft" (ngModelChange)="draftLength.set($event.trim().length)" data-testid="forum.thread.replyBody"></textarea>
            <small class="hint">{{ 'forum.markdownHint' | t }}</small>
            <div class="row">
              <p-button type="submit" icon="pi pi-send" [label]="'forum.thread.send' | t" [disabled]="draftLength() === 0" [loading]="reply.busy()" data-testid="forum.thread.send" />
            </div>
          </form>
        } @else if (t.status === 'CLOSED') {
          <p class="muted" data-testid="forum.thread.closedNote">{{ 'forum.thread.closedNote' | t }}</p>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 0.75rem; width: min(100%, 52rem); margin: 0 auto; }
    .bar, .tags, .moderation, .rename, .follow, .row { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; }
    .bar { justify-content: space-between; }
    .follow { margin: 0; padding: 0; border: 0; gap: 1rem; }
    .follow .check { display: inline-flex; align-items: center; gap: 0.4rem; min-height: 2.5rem; }
    .moderation, .rename, .composer { padding: 0.75rem 1rem; }
    .rename input { flex: 1 1 14rem; }
    .posts { display: grid; gap: 0.75rem; }
    .badge { display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.15rem 0.6rem; border-radius: 999px; border: 1px solid var(--fs-border); background: var(--fs-surface-muted); color: var(--fs-text); font-size: 0.85rem; font-weight: 600; }
    .badge--warn { border-color: var(--fs-warning-border); background: var(--fs-warning-soft); color: var(--fs-warning-text); }
    .badge--primary { border-color: var(--fs-primary); background: var(--fs-primary-soft); color: var(--fs-primary-strong); }
    .composer { display: grid; gap: 0.4rem; }
    .composer label { font-weight: 600; }
    .row { justify-content: flex-end; }
    .hint, .muted { color: var(--fs-text-muted); }
  `,
})
export class ThreadPageComponent {
  private readonly api = inject(ForumService);
  private readonly router = inject(Router);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly composer = viewChild<ElementRef<HTMLTextAreaElement>>('composer');

  readonly threadId = input.required<string>();

  protected draft = '';
  protected readonly draftLength = signal(0);
  protected newTitle = '';
  protected readonly renaming = signal(false);

  protected readonly thread = loadResource<ForumThread, string>({
    params: () => this.threadId(),
    loader: (threadId) => this.api.getForumThread({ threadId }),
    isEmpty: () => false,
  });

  constructor() {
    const realtime = inject(RealtimeService);
    realtime
      .on(RealtimeEventType.ForumPost)
      .pipe(
        filter((event) => (event.payload as { threadId?: string } | undefined)?.threadId === this.threadId()),
        debounceTime(300),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.thread.reload());
    const unregister = realtime.onReconnect(() => this.thread.reload());
    inject(DestroyRef).onDestroy(unregister);
    // deep links from bells: #post-<id> scrolls once the posts are rendered
    let scrolled = false;
    effect(() => {
      if (scrolled || this.thread.status() !== 'success' || typeof location === 'undefined' || !location.hash.startsWith('#post-')) return;
      scrolled = true;
      setTimeout(() => document.getElementById(location.hash.slice(1))?.scrollIntoView({ block: 'start' }));
    });
  }

  protected backLink(t: ForumThread): string[] {
    return t.ambassadorForum ? ['/regions', t.regionId, 'forum', 'ambassadors'] : ['/regions', t.regionId, 'forum'];
  }

  protected toggleStatus(t: ForumThread): void {
    void this.update.run({ status: t.status === ThreadStatus.Open ? ThreadStatus.Closed : ThreadStatus.Open });
  }

  protected startRename(t: ForumThread): void {
    this.newTitle = t.title;
    this.renaming.set(true);
  }

  protected quoteInto(post: ForumPost): void {
    this.draft = quotePost(post.author.displayName, post.body ?? '', this.i18n.t('forum.thread.wrote'), this.draft);
    this.draftLength.set(this.draft.trim().length);
    const el = this.composer()?.nativeElement;
    if (el) {
      el.value = this.draft;
      el.focus();
      el.setSelectionRange(this.draft.length, this.draft.length);
    }
  }

  protected readonly update = runMutation(
    (forumThreadUpdate: ForumThreadUpdate) => this.api.updateForumThread({ threadId: this.threadId(), forumThreadUpdate }),
    {
      successToastKey: 'forum.thread.saved',
      onSuccess: () => {
        this.renaming.set(false);
        this.thread.reload();
      },
    },
  );

  protected readonly follow = runMutation(
    (forumFollow: { bell: boolean; mail: boolean }) => this.api.followForumThread({ threadId: this.threadId(), forumFollow }),
    { successToastKey: 'forum.followSaved', onSuccess: () => this.thread.reload() },
  );

  protected readonly reply = runMutation(
    () => this.api.createForumPost({ threadId: this.threadId(), forumPostWrite: { body: this.draft.trim() } }),
    {
      onSuccess: (post: ForumPost) => {
        this.draft = '';
        this.draftLength.set(0);
        this.thread.reload();
        setTimeout(() => document.getElementById(`post-${post.id}`)?.scrollIntoView({ block: 'nearest' }), 300);
      },
    },
  );

  protected readonly remove = runMutation((t: ForumThread) => this.api.deleteForumThread({ threadId: t.id }), {
    successToastKey: 'forum.thread.deleted',
    onSuccess: () => {
      const t = this.thread.data();
      if (t) void this.router.navigate(this.backLink(t));
    },
  });

  protected confirmDelete(t: ForumThread): void {
    this.confirm.confirm({
      header: this.i18n.t('forum.thread.deleteTitle'),
      message: this.i18n.t('forum.thread.deleteText', { title: t.title }),
      acceptLabel: this.i18n.t('forum.thread.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(t),
    });
  }
}
