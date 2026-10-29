import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { ForumService, ForumThread, RegionDetail, RegionsService } from '../../../api';
import { isAmbassador } from '../../../core/auth/permission.util';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';

/**
 * /regions/:regionId/forum[/ambassadors]/new (F2): title, body (Markdown), follow switches. In moderated regions
 * a banner explains that moderators activate the thread first (A36 `mayStartUnmoderatedThread`).
 */
@Component({
  selector: 'fs-new-thread-page',
  imports: [...DATA_STATE_IMPORTS, Button, Checkbox, FormsModule, InputText, PageHeaderComponent, Textarea, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="'forum.new.title' | t" icon="pi pi-comments" [backLink]="backLink()" [backLabel]="(ambassadorForum() ? 'forum.ambassadors' : 'forum.title') | t" />
    <fs-data-state [state]="region" skeleton="form" data-testid="forum.new.state">
      <ng-template [fsData]="region" let-r>
        <form class="form fs-card" (ngSubmit)="create.run()">
          @if (r.moderated && !ambassadorForum() && !ambassador()) {
            <p class="notice" role="note" data-testid="forum.new.moderated"><i class="pi pi-info-circle" aria-hidden="true"></i> {{ 'forum.new.moderated' | t }}</p>
          }
          <label for="thread-title">{{ 'forum.new.titleLabel' | t }}</label>
          <input pInputText id="thread-title" name="title" maxlength="200" required [(ngModel)]="title" (ngModelChange)="touched.set(touched() + 1)" data-testid="forum.new.titleInput" />
          <label for="thread-body">{{ 'forum.new.body' | t }}</label>
          <textarea pTextarea id="thread-body" name="body" rows="8" required [(ngModel)]="body" (ngModelChange)="touched.set(touched() + 1)" data-testid="forum.new.body"></textarea>
          <small class="hint">{{ 'forum.markdownHint' | t }}</small>
          <div class="checks">
            <span class="check"><p-checkbox name="bell" [binary]="true" [(ngModel)]="followBell" inputId="thread-bell" data-testid="forum.new.followBell" /> <label for="thread-bell">{{ 'forum.follow.bell' | t }}</label></span>
            <span class="check"><p-checkbox name="mail" [binary]="true" [(ngModel)]="followMail" inputId="thread-mail" data-testid="forum.new.followMail" /> <label for="thread-mail">{{ 'forum.follow.mail' | t }}</label></span>
          </div>
          <div class="actions">
            <p-button type="submit" [label]="'forum.new.submit' | t" icon="pi pi-send" [disabled]="!ready()" [loading]="create.busy()" data-testid="forum.new.submit" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 48rem); margin: 0 auto; }
    .form { display: grid; gap: 0.4rem; padding: 1rem; }
    label { margin-top: 0.4rem; font-weight: 600; }
    .checks { display: flex; flex-wrap: wrap; gap: 1rem; }
    .checks .check { display: inline-flex; align-items: center; gap: 0.4rem; font-weight: 400; min-height: 2.5rem; }
    .notice { display: flex; gap: 0.5rem; margin: 0 0 0.5rem; padding: 0.75rem; border-radius: var(--fs-radius-md); background: var(--fs-primary-soft); color: var(--fs-text); }
    .hint { color: var(--fs-text-muted); }
    .actions { display: flex; justify-content: flex-end; margin-top: 0.5rem; }
  `,
})
export class NewThreadPageComponent {
  private readonly api = inject(ForumService);
  private readonly regionsApi = inject(RegionsService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  readonly regionId = input.required<string>();
  readonly ambassadorForum = input(false, { transform: (value: unknown) => value === true });

  protected title = '';
  protected body = '';
  protected followBell = true;
  protected followMail = false;
  protected readonly touched = signal(0);

  protected readonly ambassador = computed(() => isAmbassador(this.session.session(), this.regionId()));
  protected readonly backLink = computed(() =>
    this.ambassadorForum() ? ['/regions', this.regionId(), 'forum', 'ambassadors'] : ['/regions', this.regionId(), 'forum'],
  );
  protected readonly ready = computed(() => {
    this.touched();
    return this.title.trim().length > 0 && this.body.trim().length > 0;
  });

  protected readonly region = loadResource<RegionDetail, string>({
    params: () => this.regionId(),
    loader: (regionId) => this.regionsApi.getRegion({ regionId }),
    isEmpty: () => false,
  });

  protected readonly create = runMutation(
    () =>
      this.api.createForumThread({
        regionId: this.regionId(),
        ambassadorForum: this.ambassadorForum(),
        forumThreadCreate: { title: this.title.trim(), body: this.body.trim(), followBell: this.followBell, followMail: this.followMail },
      }),
    {
      successToastKey: 'forum.new.created',
      onSuccess: (thread: ForumThread) => void this.router.navigate(['/forum/threads', thread.id]),
    },
  );
}
