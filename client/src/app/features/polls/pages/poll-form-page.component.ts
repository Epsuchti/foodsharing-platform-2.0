import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Button } from '@openng/optimus-ui/button';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Select } from '@openng/optimus-ui/select';
import { Textarea } from '@openng/optimus-ui/textarea';
import { ToggleSwitch } from '@openng/optimus-ui/toggleswitch';
import { of } from 'rxjs';

import { Poll, PollScope, PollScopeCount, PollType, PollsService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { runMutation } from '../../../core/data/mutation';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { fromLocalInput, toLocalInput } from '../../events/events.resource';
import { datesValid, optionErrors } from '../polls.resource';

/**
 * /polls/new?regionId= and /polls/:pollId/edit (V1): type, scope with eligible counts, options editor (add,
 * remove, reorder; 2–50, unique), start ≥ now + 1 h before end, shuffle and notify switches. Editing (author,
 * before the start) changes name, description, options and shuffle only (A37c).
 */
@Component({
  selector: 'fs-poll-form-page',
  imports: [...DATA_STATE_IMPORTS, Button, FormsModule, InputText, PageHeaderComponent, Select, Textarea, ToggleSwitch, TPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header [title]="(pollId() ? 'polls.form.editTitle' : 'polls.form.newTitle') | t" icon="pi pi-chart-bar" [backLink]="backLink()" [backLabel]="'polls.title' | t" />
    <fs-data-state [state]="existing" skeleton="form" data-testid="polls.form.state">
      <ng-template [fsData]="existing" let-_>
        <form class="form fs-card" (ngSubmit)="submit()" novalidate>
          <label for="poll-name">{{ 'polls.form.name' | t }}</label>
          <input pInputText id="poll-name" name="name" maxlength="200" [ngModel]="name()" (ngModelChange)="name.set($event)" [attr.aria-invalid]="submitted() && !name().trim()" data-testid="polls.form.name" />
          <label for="poll-description">{{ 'polls.form.description' | t }}</label>
          <textarea pTextarea id="poll-description" name="description" rows="3" [ngModel]="description()" (ngModelChange)="description.set($event)" data-testid="polls.form.description"></textarea>
          @if (!pollId()) {
            <label for="poll-type">{{ 'polls.form.type' | t }}</label>
            <p-select inputId="poll-type" name="type" [options]="typeOptions()" optionLabel="label" optionValue="value" [ngModel]="type()" (ngModelChange)="type.set($event)" appendTo="body" data-testid="polls.form.type" />
            <label for="poll-scope">{{ 'polls.form.scope' | t }}</label>
            <p-select inputId="poll-scope" name="scope" [options]="scopeOptions()" optionLabel="label" optionValue="value" [ngModel]="scope()" (ngModelChange)="scope.set($event)" appendTo="body" data-testid="polls.form.scope" />
            <div class="row">
              <span class="field">
                <label for="poll-start">{{ 'polls.form.startsAt' | t }}</label>
                <input pInputText type="datetime-local" id="poll-start" name="startsAt" [ngModel]="startsAt()" (ngModelChange)="startsAt.set($event)" data-testid="polls.form.startsAt" />
              </span>
              <span class="field">
                <label for="poll-end">{{ 'polls.form.endsAt' | t }}</label>
                <input pInputText type="datetime-local" id="poll-end" name="endsAt" [ngModel]="endsAt()" (ngModelChange)="endsAt.set($event)" data-testid="polls.form.endsAt" />
              </span>
            </div>
            <small [class.error]="submitted() && !datesOk()" class="hint" data-testid="polls.form.datesHint">{{ 'polls.form.leadTime' | t }}</small>
          }
          <fieldset class="options">
            <legend>{{ 'polls.form.options' | t }}</legend>
            @for (o of options(); track $index; let i = $index, first = $first, last = $last) {
              <div class="option" data-testid="polls.form.option">
                <label class="fs-visually-hidden" [for]="'poll-option-' + i">{{ 'polls.form.option' | t: { n: i + 1 } }}</label>
                <input pInputText [id]="'poll-option-' + i" [name]="'option' + i" maxlength="500" [ngModel]="o" (ngModelChange)="setOption(i, $event)" data-testid="polls.form.optionInput" />
                <p-button icon="pi pi-arrow-up" [text]="true" [disabled]="first" [ariaLabel]="'polls.form.up' | t" (onClick)="move(i, -1)" />
                <p-button icon="pi pi-arrow-down" [text]="true" [disabled]="last" [ariaLabel]="'polls.form.down' | t" (onClick)="move(i, 1)" />
                <p-button icon="pi pi-times" severity="danger" [text]="true" [ariaLabel]="'polls.form.remove' | t" (onClick)="remove(i)" data-testid="polls.form.removeOption" />
              </div>
            }
            <p-button icon="pi pi-plus" [label]="'polls.form.addOption' | t" [text]="true" (onClick)="options.set([...options(), ''])" data-testid="polls.form.addOption" />
            @if (submitted() && optionError(); as code) {
              <small class="error" data-testid="polls.form.optionsError" [attr.data-code]="code">{{ 'polls.form.optionsError.' + code | t }}</small>
            }
          </fieldset>
          <span class="switch">
            <p-toggleswitch inputId="poll-shuffle" name="shuffle" [ngModel]="shuffle()" (ngModelChange)="shuffle.set($event)" data-testid="polls.form.shuffle" />
            <label for="poll-shuffle">{{ 'polls.form.shuffle' | t }}</label>
          </span>
          @if (!pollId()) {
            <span class="switch">
              <p-toggleswitch inputId="poll-notify" name="notify" [ngModel]="notify()" (ngModelChange)="notify.set($event)" data-testid="polls.form.notify" />
              <label for="poll-notify">{{ 'polls.form.notify' | t }}</label>
            </span>
          }
          @if (serverError(); as code) {
            <small class="error" data-testid="polls.form.serverError" [attr.data-code]="code">{{ 'errors.' + code | t }}</small>
          }
          <div class="actions">
            <p-button type="submit" icon="pi pi-check" [label]="(pollId() ? 'common.save' : 'polls.form.create') | t" [loading]="save.busy()" data-testid="polls.form.submit" />
          </div>
        </form>
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host { display: block; width: min(100%, 44rem); margin: 0 auto; }
    .form { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.4rem; padding: 1rem; min-width: 0; }
    .form input, .form textarea { width: 100%; min-width: 0; box-sizing: border-box; }
    label, legend { margin-top: 0.4rem; font-weight: 600; }
    fieldset { display: grid; gap: 0.4rem; margin: 0; padding: 0; border: 0; }
    .option { display: flex; align-items: center; gap: 0.25rem; }
    .option input { flex: 1; width: auto; }
    .option label { flex: none; }
    .row { display: flex; flex-wrap: wrap; gap: 0.75rem; }
    .field { display: grid; gap: 0.25rem; flex: 1 1 12rem; min-width: 0; }
    .switch { display: inline-flex; align-items: center; gap: 0.5rem; min-height: 2.5rem; }
    .switch label { margin: 0; font-weight: 400; }
    .hint { color: var(--fs-text-muted); }
    .error { color: var(--fs-danger); }
    .actions { display: flex; justify-content: flex-end; margin-top: 0.5rem; }
  `,
})
export class PollFormPageComponent {
  private readonly api = inject(PollsService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  readonly pollId = input<string | undefined>(undefined);
  readonly regionId = input<string | undefined>(undefined);

  protected readonly name = signal('');
  protected readonly description = signal('');
  protected readonly type = signal<PollType>(PollType.SelectOne);
  protected readonly scope = signal<PollScope>(PollScope.Foodsavers);
  protected readonly startsAt = signal('');
  protected readonly endsAt = signal('');
  protected readonly options = signal<string[]>(['', '']);
  protected readonly shuffle = signal(false);
  protected readonly notify = signal(true);
  protected readonly submitted = signal(false);
  protected readonly serverError = signal<string | null>(null);

  protected readonly optionError = computed(() => optionErrors(this.options()));
  protected readonly datesOk = computed(() => !!this.pollId() || datesValid(this.startsAt(), this.endsAt(), Date.now()));
  protected readonly backLink = computed(() =>
    this.pollId() ? ['/polls', this.pollId()] : this.regionId() ? ['/regions', this.regionId(), 'polls'] : ['/polls/mine'],
  );

  protected readonly existing = loadResource<Poll | null, string>({
    params: () => this.pollId() ?? '',
    loader: (pollId) => (pollId ? this.api.getPoll({ pollId }) : of(null)),
    isEmpty: () => false,
  });

  private readonly counts = loadResource<PollScopeCount[], string | undefined>({
    params: () => (this.pollId() ? undefined : this.regionId()),
    loader: (regionId) => this.api.getPollScopeCounts({ regionId: regionId! }),
  });

  protected readonly typeOptions = computed(() => {
    this.i18n.language();
    return Object.values(PollType).map((value) => ({ value, label: this.i18n.t(`polls.type.${value}`) }));
  });

  protected readonly scopeOptions = computed(() => {
    this.i18n.language();
    const counts = this.counts.data();
    const scopes = counts?.map((c) => c.scope) ?? Object.values(PollScope);
    return scopes.map((value) => {
      const count = counts?.find((c) => c.scope === value)?.count;
      const label = this.i18n.t(`polls.scope.${value}`);
      return { value, label: count == null ? label : `${label} (${count})` };
    });
  });

  constructor() {
    effect(() => {
      const p = this.existing.data();
      if (!p) return;
      untracked(() => {
        this.name.set(p.name);
        this.description.set(p.description ?? '');
        this.options.set(p.options.map((o) => o.text));
        this.shuffle.set(p.shuffleOptions);
        this.startsAt.set(toLocalInput(p.startsAt));
        this.endsAt.set(toLocalInput(p.endsAt));
      });
    });
  }

  protected setOption(index: number, value: string): void {
    this.options.update((list) => list.map((o, i) => (i === index ? value : o)));
  }

  protected move(index: number, delta: number): void {
    this.options.update((list) => {
      const next = [...list];
      [next[index], next[index + delta]] = [next[index + delta], next[index]];
      return next;
    });
  }

  protected remove(index: number): void {
    this.options.update((list) => list.filter((_, i) => i !== index));
  }

  protected submit(): void {
    this.submitted.set(true);
    if (this.name().trim() && !this.optionError() && this.datesOk() && (this.pollId() || this.regionId())) void this.save.run();
  }

  protected readonly save = runMutation(
    () => {
      this.serverError.set(null);
      const options = this.options().map((o) => o.trim());
      const pollId = this.pollId();
      return pollId
        ? this.api.updatePoll({ pollId, pollUpdate: { name: this.name().trim(), description: this.description(), options, shuffleOptions: this.shuffle() } })
        : this.api.createPoll({
            pollCreate: {
              regionId: this.regionId()!,
              name: this.name().trim(),
              description: this.description(),
              type: this.type(),
              scope: this.scope(),
              startsAt: fromLocalInput(this.startsAt()),
              endsAt: fromLocalInput(this.endsAt()),
              options,
              shuffleOptions: this.shuffle(),
              notifyVoters: this.notify(),
            },
          });
    },
    {
      successToastKey: 'polls.form.saved',
      errorToast: false,
      onError: (error) => this.serverError.set(error.code ?? 'UNKNOWN'),
      onSuccess: (poll: Poll) => void this.router.navigate(['/polls', poll.id]),
    },
  );
}
