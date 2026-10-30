import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { map } from 'rxjs';

import {
  AdminUsersService,
  Banana,
  BasketSummary,
  BasketsService,
  Commitments,
  Conversation,
  FeatureToggleKey,
  HistorySubjectType,
  MessagingService,
  ProfilesService,
  PublicProfile,
  Role,
  UserNote,
  WallEntryPage,
} from '../../../api';
import { SessionService } from '../../../core/auth/session.service';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { DateTimePipe } from '../../../core/dates/date-time.pipe';
import { FeatureToggleService } from '../../../core/feature-toggles/feature-toggle.service';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { M6_PAGES_READY } from '../../../layout/nav.config';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { RequiredMessageDialogComponent } from '../../../shared/required-message-dialog/required-message-dialog.component';
import { WallComponent, WallPost } from '../../../shared/wall/wall.component';
import { ReportDialogComponent } from '../../reports/components/report-dialog.component';
import { UserReportsComponent } from '../../reports/components/user-reports.component';
import { BananaListComponent } from '../components/banana-list.component';
import { ProfileCardComponent } from '../components/profile-card.component';
import { ProfileStatsComponent } from '../components/profile-stats.component';
import { ProfilesResources } from '../profiles.resource';
import { BANANA_MAX_LENGTH, BANANA_MIN_LENGTH, ProfileTab, achievementIcon, activeTab, buddyActions, profileTabs } from '../profiles.util';

const TAB_ICONS: Record<ProfileTab, string> = {
  wall: 'pi pi-comments',
  bananas: 'pi pi-thumbs-up',
  commitments: 'pi pi-calendar',
  notes: 'pi pi-lock',
  history: 'pi pi-history',
};

/** Weeks shown in the commitments tab. */
const COMMITMENT_WEEKS = 12;

/**
 * /profile/:userId (A6, A15, A16, G4; A8 profile history): head card with actions (message, banana, buddy, edit,
 * report → `fs-report-dialog`), stat tiles, the reports block for report handlers (`violationCount`), regions and groups, stores (when the viewer may see them), achievements,
 * KAM chains, active baskets (feature BASKETS) and the tabs wall | bananas | commitments | notes | history
 * (`?tab=`, gated by `ProfilePermissions`). The server decides every right; a 404 renders the not-found state.
 */
@Component({
  selector: 'fs-profile-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    BananaListComponent,
    Button,
    DateTimePipe,
    HistoryTimelineComponent,
    ProfileCardComponent,
    ProfileStatsComponent,
    ReportDialogComponent,
    RequiredMessageDialogComponent,
    RouterLink,
    TPipe,
    UserReportsComponent,
    WallComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.scss',
})
export class ProfilePageComponent {
  private readonly api = inject(ProfilesService);
  private readonly adminUsers = inject(AdminUsersService);
  private readonly baskets = inject(BasketsService);
  private readonly messaging = inject(MessagingService);
  private readonly resources = inject(ProfilesResources);
  private readonly session = inject(SessionService);
  private readonly features = inject(FeatureToggleService);
  private readonly confirm = inject(ConfirmationService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly userId = toSignal(this.route.paramMap.pipe(map((params) => params.get('userId') ?? undefined)));
  private readonly requestedTab = toSignal(this.route.queryParamMap.pipe(map((params) => params.get('tab'))));

  protected readonly viewerId = computed(() => this.session.user()?.id);
  protected readonly orga = computed(() => this.session.hasRole(Role.Orga));
  protected readonly reportsReady = M6_PAGES_READY.reports;
  /** Report dialog (features/reports, G1). */
  protected readonly reportDialogOpen = signal(false);
  protected readonly subjectType = HistorySubjectType.User;
  protected readonly bananaMin = BANANA_MIN_LENGTH;
  protected readonly bananaMax = BANANA_MAX_LENGTH;
  protected readonly tabIcons = TAB_ICONS;
  protected readonly achievementIcon = achievementIcon;

  protected readonly profile = loadResource<PublicProfile, string>({
    params: () => this.userId(),
    loader: (userId) => this.api.getPublicProfile({ userId }),
    isEmpty: () => false,
  });

  protected readonly self = computed(() => !!this.viewerId() && this.viewerId() === this.userId());
  protected readonly tabs = computed(() => {
    const p = this.profile.data();
    return p ? profileTabs(p) : [];
  });
  protected readonly tab = computed<ProfileTab>(() => {
    const p = this.profile.data();
    return p ? activeTab(p, this.requestedTab()) : 'wall';
  });
  protected readonly buddy = computed(() => {
    const p = this.profile.data();
    return p ? buddyActions(p.buddy.status, this.self()) : [];
  });

  /** Id of the loaded profile; a computed, so reloading the profile does not reload the tab data. */
  private readonly loadedId = computed(() => this.profile.data()?.user.id);

  /** Tab data loads only while its tab is open (params undefined = idle). */
  private tabParams(tab: ProfileTab): string | undefined {
    return this.tab() === tab ? this.loadedId() : undefined;
  }

  // --- wall (everyone signed in reads, the owner writes; A46) ---
  protected readonly wall = loadResource<WallEntryPage, string>({
    params: () => this.tabParams('wall'),
    loader: (userId) => this.api.listUserWallPosts({ userId, size: 50 }),
  });
  protected readonly wallPosts = computed<WallPost[]>(() => this.wall.data()?.items ?? []);
  protected readonly postWall = runMutation(
    (body: string) => this.api.createUserWallPost({ userId: this.userId()!, wallEntryCreate: { body } }),
    { onSuccess: () => this.wall.reload() },
  );
  protected readonly deleteWall = runMutation(
    (post: WallPost) => this.api.deleteUserWallPost({ userId: this.userId()!, postId: post.id }),
    { successToastKey: 'profiles.toast.wallDeleted', onSuccess: () => this.wall.reload() },
  );

  // --- bananas (A15, G4) ---
  protected readonly bananaDirection = signal<'received' | 'sent'>('received');
  protected readonly bananas = loadResource<Banana[], { userId: string; direction: 'received' | 'sent' }>({
    params: () => {
      const userId = this.tabParams('bananas');
      return userId ? { userId, direction: this.bananaDirection() } : undefined;
    },
    loader: ({ userId, direction }) => this.api.listBananas({ userId, direction }),
  });
  protected readonly bananaDialogOpen = signal(false);
  protected readonly giveBanana = runMutation(
    (message: string) => this.api.giveBanana({ userId: this.userId()!, bananaCreate: { message } }),
    {
      successToastKey: 'profiles.toast.bananaGiven',
      onSuccess: () => {
        this.bananaDialogOpen.set(false);
        this.profile.reload();
        this.bananas.reload();
      },
    },
  );
  protected readonly deleteBanana = runMutation(
    (banana: Banana) => this.api.deleteBanana({ userId: banana.recipient.id, senderId: banana.sender.id }),
    {
      successToastKey: 'profiles.toast.bananaDeleted',
      onSuccess: () => {
        this.profile.reload();
        this.bananas.reload();
      },
    },
  );

  // --- commitments ---
  protected readonly commitments = loadResource<Commitments, string>({
    params: () => this.tabParams('commitments'),
    loader: (userId) => this.api.getUserCommitments({ userId, weeks: COMMITMENT_WEEKS }),
    isEmpty: (value) => value.weeks.length === 0,
  });
  protected readonly commitmentWeeks = computed(() => [...(this.commitments.data()?.weeks ?? [])].reverse());

  // --- admin notes (admin-users API, mayAdministrateUser) ---
  protected readonly notes = loadResource<UserNote[], string>({
    params: () => this.tabParams('notes'),
    loader: (userId) => this.adminUsers.listUserNotes({ userId }),
  });
  protected readonly addNote = runMutation(
    (body: string) => this.adminUsers.addUserNote({ userId: this.userId()!, userNoteCreate: { body } }),
    { successToastKey: 'profiles.toast.noteAdded', onSuccess: () => this.notes.reload() },
  );
  protected readonly deleteNote = runMutation(
    (note: WallPost) => this.adminUsers.deleteUserNote({ userId: this.userId()!, noteId: note.id }),
    { successToastKey: 'profiles.toast.noteDeleted', onSuccess: () => this.notes.reload() },
  );

  // --- history (A8 profile piece) ---
  protected readonly history = computed(() => {
    const userId = this.tabParams('history');
    return userId ? this.resources.changeHistory(userId) : null;
  });

  // --- active baskets (feature BASKETS, Q5 builds the basket pages) ---
  protected readonly userBaskets = loadResource<BasketSummary[], string>({
    params: () => (this.features.isEnabled(FeatureToggleKey.Baskets) ? this.loadedId() : undefined),
    loader: (userId) => this.baskets.listUserBaskets({ userId }),
  });

  // --- buddies (A16) ---
  protected readonly requestBuddy = runMutation(() => this.api.requestBuddy({ userId: this.userId()! }), {
    successToastKey: 'profiles.toast.buddyRequested',
    onSuccess: () => this.profile.reload(),
  });
  protected readonly acceptBuddy = runMutation(() => this.api.requestBuddy({ userId: this.userId()! }), {
    successToastKey: 'profiles.toast.buddyAccepted',
    onSuccess: () => this.profile.reload(),
  });
  protected readonly removeBuddy = runMutation(() => this.api.removeBuddy({ userId: this.userId()! }), {
    successToastKey: 'profiles.toast.buddyRemoved',
    onSuccess: () => this.profile.reload(),
  });

  // --- message: find or create the direct conversation, then open it ---
  protected readonly message = runMutation(
    () => this.messaging.lookupConversation({ conversationLookup: { memberIds: [this.userId()!] } }),
    { onSuccess: (conversation: Conversation) => void this.router.navigate(['/messages', conversation.id]) },
  );

  protected editLink(p: PublicProfile): string[] {
    return this.self() ? ['/account/profile'] : ['/users', p.user.id];
  }

  protected setBananaDirection(direction: 'received' | 'sent'): void {
    this.bananaDirection.set(direction);
  }

  protected confirmRemoveBuddy(p: PublicProfile): void {
    this.confirm.confirm({
      header: this.i18n.t('profiles.buddy.removeTitle'),
      message: this.i18n.t('profiles.buddy.removeText', { name: p.user.displayName }),
      acceptLabel: this.i18n.t('profiles.buddy.remove'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.removeBuddy.run(),
    });
  }

  protected confirmDeleteBanana(banana: Banana): void {
    this.confirm.confirm({
      header: this.i18n.t('profiles.bananas.deleteTitle'),
      message: this.i18n.t('profiles.bananas.deleteText'),
      acceptLabel: this.i18n.t('profiles.bananas.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-text p-button-secondary',
      accept: () => void this.deleteBanana.run(banana),
    });
  }
}
