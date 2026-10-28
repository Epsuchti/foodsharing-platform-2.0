import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Textarea } from '@openng/optimus-ui/textarea';

import { StoresService, WallMilestone, WallPost, WallPostPage } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';
import { StorePageContext } from '../store-page.context';

const PAGE_SIZE = 20;
const MAX_LENGTH = 4000;

const MILESTONE_ICONS: Record<WallMilestone, string> = {
  [WallMilestone.None]: 'pi pi-comment',
  [WallMilestone.StoreCreated]: 'pi pi-flag',
  [WallMilestone.CooperationStarted]: 'pi pi-star',
  [WallMilestone.StatusChanged]: 'pi pi-sync',
  [WallMilestone.TeamStatusChanged]: 'pi pi-users',
};

/**
 * /stores/:storeId/wall (S11): the team's notes. Composer for members (mayPostWall), milestone cards (store created,
 * cooperation started, status changes) styled apart from posts, delete per post (`mayDelete`, decision 54) and
 * "load more" paging.
 */
@Component({
  selector: 'fs-store-wall-tab',
  imports: [...DATA_STATE_IMPORTS, Button, DateTimePipe, FormsModule, Textarea, TPipe, UserChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (ctx.may('mayPostWall')) {
      <form class="fs-card composer" (ngSubmit)="post.run()" data-testid="stores.wall.composer">
        <label class="composer__label" for="store-wall-text">{{ 'stores.wall.composerLabel' | t }}</label>
        <textarea
          pTextarea
          id="store-wall-text"
          name="text"
          rows="3"
          [maxlength]="maxLength"
          [ngModel]="text()"
          (ngModelChange)="text.set($event)"
          [placeholder]="'stores.wall.placeholder' | t"
          data-testid="stores.wall.text"
        ></textarea>
        <div class="composer__foot">
          <small class="muted">{{ text().length }} / {{ maxLength }}</small>
          <p-button
            type="submit"
            [label]="'stores.wall.post' | t"
            icon="pi pi-send"
            size="small"
            [disabled]="!text().trim()"
            [loading]="post.busy()"
            data-testid="stores.wall.submit"
          />
        </div>
      </form>
    }

    <fs-data-state
      [state]="posts"
      skeleton="list"
      [emptyTitle]="'stores.wall.empty' | t"
      [emptyText]="'stores.wall.emptyText' | t"
      [askWho]="'stores.page.askWho' | t"
      testId="stores.wall.state"
      data-testid="stores.wall.state"
    >
      <ng-template [fsData]="posts">
        <ol class="posts" data-testid="stores.wall.list">
          @for (item of items(); track item.id) {
            <li
              class="fs-card post"
              [class.post--milestone]="item.milestone !== 'NONE'"
              data-testid="stores.wall.post"
              [attr.data-post-id]="item.id"
              [attr.data-milestone]="item.milestone"
            >
              @if (item.milestone !== 'NONE') {
                <div class="milestone" data-testid="stores.wall.milestone">
                  <span class="milestone__icon"><i [class]="icon(item.milestone)" aria-hidden="true"></i></span>
                  <div class="milestone__body">
                    <strong>{{ 'stores.wall.milestone.' + item.milestone | t }}</strong>
                    <span class="muted">{{ item.createdAt | fsDate: 'dateLong' : ctx.timeZone() }}</span>
                    @if (item.text) {
                      <p class="post__text">{{ item.text }}</p>
                    }
                  </div>
                </div>
              } @else {
                <div class="post__head">
                  @if (item.author; as author) {
                    <fs-user-chip [name]="author.displayName" [userId]="author.id" [avatarUrl]="author.avatarUrl" [subtitle]="item.createdAt | fsDate: 'dateTime' : ctx.timeZone()" />
                  } @else {
                    <span class="muted">{{ item.createdAt | fsDate: 'dateTime' : ctx.timeZone() }}</span>
                  }
                </div>
                <p class="post__text" data-testid="stores.wall.postText">{{ item.text }}</p>
              }
              @if (item.mayDelete) {
                <p-button
                  class="post__delete"
                  icon="pi pi-trash"
                  severity="danger"
                  [text]="true"
                  [rounded]="true"
                  size="small"
                  [ariaLabel]="'stores.wall.delete' | t"
                  (onClick)="askDelete(item)"
                  data-testid="stores.wall.delete"
                />
              }
            </li>
          }
        </ol>
        @if (hasMore()) {
          <div class="more">
            <p-button
              [label]="'stores.wall.more' | t"
              icon="pi pi-angle-down"
              severity="secondary"
              [outlined]="true"
              [loading]="posts.status() === 'refreshing'"
              (onClick)="loadMore()"
              data-testid="stores.wall.more"
            />
          </div>
        }
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: grid; gap: 1rem; }
    .composer { display: grid; gap: 0.5rem; padding: 1rem; }
    .composer__label { font-weight: 600; }
    .composer textarea { width: 100%; resize: vertical; }
    .composer__foot { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; }
    .muted { color: var(--fs-text-muted); font-size: 0.85rem; }
    .posts { display: grid; gap: 0.6rem; margin: 0; padding: 0; list-style: none; }
    .post { position: relative; display: grid; gap: 0.4rem; padding: 0.9rem 3rem 0.9rem 1rem; }
    .post--milestone { border-left: 4px solid var(--fs-primary); background: var(--fs-surface-muted); }
    .post__text { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
    .post__delete { position: absolute; top: 0.5rem; right: 0.5rem; }
    .milestone { display: flex; gap: 0.75rem; align-items: flex-start; }
    .milestone__icon {
      display: inline-grid; place-items: center; flex: none; width: 2.2rem; height: 2.2rem; border-radius: 999px;
      background: var(--fs-primary); color: var(--fs-on-primary, #fff);
    }
    .milestone__body { display: grid; gap: 0.15rem; }
    .more { display: flex; justify-content: center; }
  `,
})
export class StoreWallTab {
  protected readonly ctx = inject(StorePageContext);
  private readonly api = inject(StoresService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);

  protected readonly maxLength = MAX_LENGTH;
  protected readonly text = signal('');
  private readonly size = signal(PAGE_SIZE);

  protected readonly posts = loadResource<WallPostPage, { storeId: string; size: number }>({
    params: () => {
      const storeId = this.ctx.storeId();
      // no permission gate: the server answers 403 (forbidden state) for non-readers
      return storeId ? { storeId, size: this.size() } : undefined;
    },
    loader: ({ storeId, size }) => this.api.listWallPosts({ storeId, page: 0, size }),
  });

  protected readonly items = computed(() => this.posts.data()?.items ?? []);
  protected readonly hasMore = computed(() => {
    const page = this.posts.data();
    return !!page && page.items.length < page.page.totalItems;
  });

  protected icon(milestone: WallMilestone): string {
    return MILESTONE_ICONS[milestone] ?? MILESTONE_ICONS[WallMilestone.None];
  }

  protected loadMore(): void {
    this.size.update((size) => size + PAGE_SIZE);
  }

  protected readonly post = runMutation(
    () => this.api.addWallPost({ storeId: this.ctx.storeId()!, wallPostCreate: { text: this.text().trim() } }),
    {
      successToastKey: 'stores.wall.posted',
      onSuccess: () => {
        this.text.set('');
        this.posts.reload();
      },
    },
  );

  protected readonly remove = runMutation(
    (post: WallPost) => this.api.deleteWallPost({ storeId: this.ctx.storeId()!, postId: post.id }),
    { successToastKey: 'stores.wall.deleted', onSuccess: () => this.posts.reload() },
  );

  protected askDelete(post: WallPost): void {
    this.confirm.confirm({
      header: this.i18n.t('stores.wall.deleteTitle'),
      message: this.i18n.t('stores.wall.deleteText'),
      acceptLabel: this.i18n.t('stores.wall.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.remove.run(post),
    });
  }
}
