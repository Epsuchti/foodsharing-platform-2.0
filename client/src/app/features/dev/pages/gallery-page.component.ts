import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MessageService } from '@openng/optimus-ui/api';
import { Button } from '@openng/optimus-ui/button';
import { Checkbox } from '@openng/optimus-ui/checkbox';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Message } from '@openng/optimus-ui/message';
import { Select } from '@openng/optimus-ui/select';
import { SelectButton } from '@openng/optimus-ui/selectbutton';
import { Tag } from '@openng/optimus-ui/tag';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';

import { of } from 'rxjs';

import { ApiErrorCode, PublicService, Role } from '../../../api';
import { ApiErrorView } from '../../../core/data/api-error';
import {
  DataState,
  DataStatus,
  loadResource,
  staticDataState,
} from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { isUiLanguage } from '../../../core/i18n/languages';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ThemeService, isThemePreference } from '../../../core/theme/theme.service';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { SkeletonPreset } from '../../../shared/data-state/skeleton-presets.component';
import {
  DEMO_USER_ID,
  demoActionsSource,
  createDemoHistorySource,
  emptyHistorySource,
  failingHistorySource,
  loadingHistorySource,
} from '../../../shared/history-timeline/history-demo';
import { HistoryTimelineComponent } from '../../../shared/history-timeline/history-timeline.component';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { GALLERY_SECTIONS } from '../gallery/gallery-sections';
import { UserAvatarComponent } from '../../../shared/user-chip/user-avatar.component';
import { UserChipComponent } from '../../../shared/user-chip/user-chip.component';

interface DemoPerson {
  id: string;
  name: string;
  role: Role;
}

const PEOPLE: DemoPerson[] = [
  { id: '00000001-0001-4000-8000-000000000001', name: 'Anna Muster', role: Role.Foodsaver },
  { id: '00000001-0001-4000-8000-000000000002', name: 'Luca Bianchi', role: Role.StoreManager },
  { id: '00000001-0001-4000-8000-000000000003', name: 'Chloé Martin', role: Role.Ambassador },
];

function demoError(status: number, code: ApiErrorView['code'], retryable: boolean): ApiErrorView {
  return {
    status,
    code,
    message: `${code} (gallery)`,
    traceId: '5f3a9c1e7b20',
    retryable,
    fieldErrors: [],
  };
}

/**
 * /dev/gallery (local/e2e only): every shared component in every state, for screenshots and design reviews.
 * Each section has data-testid="gallery.<component>.<state>". `?theme=dark&lang=fr` sets theme and language
 * for this visit without saving them.
 */
@Component({
  selector: 'fs-gallery-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    Button,
    Checkbox,
    FormsModule,
    HistoryTimelineComponent,
    NgComponentOutlet,
    InputText,
    Message,
    PageHeaderComponent,
    Select,
    SelectButton,
    TPipe,
    Tag,
    Textarea,
    ToggleSwitch,
    UserAvatarComponent,
    UserChipComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './gallery-page.component.html',
  styleUrl: './gallery-page.component.scss',
})
export class GalleryPageComponent implements OnInit {
  private readonly i18n = inject(I18nService);
  private readonly themeService = inject(ThemeService);
  private readonly messages = inject(MessageService);
  private readonly publicApi = inject(PublicService);

  /** A real loadResource (offline/auto-retry and request-bar e2e tests run against it). */
  protected readonly live = loadResource({ loader: () => this.publicApi.getAppInfo() });

  /** Query params (component input binding). */
  readonly theme = input<string | undefined>(undefined);
  readonly lang = input<string | undefined>(undefined);

  protected readonly people = PEOPLE;
  /** M2 shared components and per-feature sections. */
  protected readonly sections = GALLERY_SECTIONS;
  protected readonly Role = Role;
  protected readonly skeletons: SkeletonPreset[] = ['list', 'card', 'table', 'form', 'detail'];

  protected readonly states: {
    status: DataStatus;
    label: string;
    state: DataState<DemoPerson[]>;
  }[] = [
    { status: 'loading', label: 'loading', state: staticDataState<DemoPerson[]>('loading') },
    { status: 'refreshing', label: 'refreshing', state: staticDataState('refreshing', PEOPLE) },
    { status: 'success', label: 'success', state: staticDataState('success', PEOPLE) },
    { status: 'empty', label: 'empty', state: staticDataState<DemoPerson[]>('empty', []) },
    {
      status: 'error',
      label: 'error',
      state: staticDataState<DemoPerson[]>(
        'error',
        undefined,
        demoError(500, ApiErrorCode.UnexpectedError, true),
      ),
    },
    {
      status: 'error',
      label: 'errorStale',
      state: staticDataState('error', PEOPLE, demoError(503, ApiErrorCode.UnexpectedError, true)),
    },
    {
      status: 'forbidden',
      label: 'forbidden',
      state: staticDataState<DemoPerson[]>(
        'forbidden',
        undefined,
        demoError(403, ApiErrorCode.AccessDenied, false),
      ),
    },
    {
      status: 'notFound',
      label: 'notFound',
      state: staticDataState<DemoPerson[]>(
        'notFound',
        undefined,
        demoError(404, ApiErrorCode.ResourceNotFound, false),
      ),
    },
    {
      status: 'offline',
      label: 'offline',
      state: staticDataState<DemoPerson[]>('offline', undefined, demoError(0, 'NETWORK', true)),
    },
    {
      status: 'offline',
      label: 'offlineStale',
      state: staticDataState('offline', PEOPLE, demoError(0, 'NETWORK', true)),
    },
  ];

  protected readonly errors = {
    error: demoError(500, ApiErrorCode.UnexpectedError, true),
    tooMany: { ...demoError(429, ApiErrorCode.TooManyRequests, true), retryAfterSeconds: 30 },
    conflict: demoError(409, ApiErrorCode.ConcurrentModification, false),
    forbidden: demoError(403, ApiErrorCode.AccessDenied, false),
    notFound: demoError(404, ApiErrorCode.ResourceNotFound, false),
    offline: demoError(0, 'NETWORK', true),
  };

  // history timeline demo (history section)
  protected readonly history = {
    viewerId: DEMO_USER_ID,
    actions: demoActionsSource,
    filled: createDemoHistorySource(() => this.i18n.t('dev.gallery.historyReason')),
    empty: emptyHistorySource,
    loading: loadingHistorySource,
    error: failingHistorySource(demoError(500, ApiErrorCode.UnexpectedError, true)),
    noop: () => of(''),
  };

  // form kit demo values
  protected readonly name = signal('Anna Muster');
  protected readonly note = signal('');
  protected readonly city = signal<string | null>('zurich');
  protected readonly cities = [
    { label: 'Zürich', value: 'zurich' },
    { label: 'Bern', value: 'bern' },
    { label: 'Lausanne', value: 'lausanne' },
    { label: 'Lugano', value: 'lugano' },
  ];
  protected readonly agree = signal(true);
  protected readonly notify = signal(false);
  protected readonly frequency = signal('weekly');
  protected readonly frequencies = computed(() => {
    this.i18n.language();
    return ['daily', 'weekly', 'never'].map((value) => ({
      label: this.i18n.t(`dev.gallery.freq.${value}`),
      value,
    }));
  });

  ngOnInit(): void {
    const theme = this.theme();
    if (isThemePreference(theme)) {
      this.themeService.setPreference(theme, { persist: false, remember: false });
    }
    const lang = this.lang();
    if (isUiLanguage(lang)) {
      void this.i18n.setLanguage(lang, { persist: false, remember: false });
    }
  }

  protected toast(severity: 'success' | 'info' | 'warn' | 'error'): void {
    this.messages.add({
      severity,
      summary: this.i18n.t('dev.gallery.toastSummary'),
      detail: this.i18n.t('dev.gallery.toastDetail'),
      life: 6000,
    });
  }

  protected noop(): void {
    // demo buttons
  }
}
