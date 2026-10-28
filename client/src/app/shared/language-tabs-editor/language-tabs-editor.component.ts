import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { InputText } from '@openng/optimus-ui/inputtext';
import { Textarea } from '@openng/optimus-ui/textarea';

import { LANGUAGES, UiLanguage } from '../../core/i18n/languages';
import { TPipe } from '../../core/i18n/t.pipe';
import { MarkdownPipe } from '../../core/markdown/markdown.pipe';

/** Text per language (`{ de: '…', fr: '…' }`), the shape of `names{lang}`, `bodies{lang}`, … in the contract. */
export type TranslatedText = Partial<Record<UiLanguage, string>>;

let nextId = 0;

/**
 * Five-language text field for editors (quiz questions, content pages, legal documents, region onboarding steps;
 * conventions A14). One tab per language with a filled/missing marker; `requiredLanguages` must be filled (default
 * German, the fallback of every read). `multiline` uses a textarea, `markdown` adds a preview.
 *
 * `<fs-language-tabs-editor formControlName="titles" [label]="…" testId="content.editor.titles" />`
 */
@Component({
  selector: 'fs-language-tabs-editor',
  imports: [InputText, MarkdownPipe, TPipe, Textarea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => LanguageTabsEditorComponent),
      multi: true,
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => LanguageTabsEditorComponent),
      multi: true,
    },
  ],
  host: { class: 'fs-language-tabs-editor' },
  template: `
    @if (label()) {
      <span class="label" [id]="uid + '-label'">{{ label() }}</span>
    }
    <div class="tabs" role="tablist" [attr.aria-labelledby]="label() ? uid + '-label' : null">
      @for (lang of languages; track lang) {
        <button
          type="button"
          role="tab"
          class="tab"
          [class.tab--active]="lang === active()"
          [attr.aria-selected]="lang === active()"
          [attr.aria-controls]="uid + '-panel'"
          [id]="uid + '-tab-' + lang"
          (click)="active.set(lang)"
          [attr.data-testid]="testId() + '.tab.' + lang"
        >
          <span>{{ 'lang.' + lang | t }}</span>
          @if (filled(lang)) {
            <i class="pi pi-check tab__state tab__state--ok" aria-hidden="true"></i>
          } @else if (requiredLanguages().includes(lang)) {
            <i class="pi pi-circle-fill tab__state tab__state--missing" aria-hidden="true"></i>
            <span class="fs-visually-hidden">{{ 'shared.languageTabs.missing' | t }}</span>
          }
        </button>
      }
    </div>
    <div
      class="panel"
      role="tabpanel"
      [id]="uid + '-panel'"
      [attr.aria-labelledby]="uid + '-tab-' + active()"
    >
      @if (multiline()) {
        <textarea
          pTextarea
          [rows]="rows()"
          [value]="value()[active()] ?? ''"
          [disabled]="disabled()"
          [attr.lang]="active() === 'gws' ? 'de-CH' : active()"
          [attr.aria-label]="(label() ?? '') + ' (' + ('lang.' + active() | t) + ')'"
          [placeholder]="placeholder(active())"
          (input)="update(active(), $any($event.target).value)"
          (blur)="onTouched()"
          [attr.data-testid]="testId() + '.field.' + active()"
        ></textarea>
      } @else {
        <input
          pInputText
          [value]="value()[active()] ?? ''"
          [disabled]="disabled()"
          [attr.lang]="active() === 'gws' ? 'de-CH' : active()"
          [attr.aria-label]="(label() ?? '') + ' (' + ('lang.' + active() | t) + ')'"
          [placeholder]="placeholder(active())"
          (input)="update(active(), $any($event.target).value)"
          (blur)="onTouched()"
          [attr.data-testid]="testId() + '.field.' + active()"
        />
      }
      <div class="foot">
        @if (missingRequired().length > 0) {
          <small class="foot__missing">
            {{ 'shared.languageTabs.required' | t: { languages: missingNames() } }}
          </small>
        } @else {
          <small>{{ 'shared.languageTabs.fallback' | t }}</small>
        }
        @if (markdown() && (value()[active()] ?? '').trim()) {
          <button type="button" class="preview-toggle" (click)="preview.set(!preview())">
            {{ (preview() ? 'shared.languageTabs.hidePreview' : 'shared.languageTabs.showPreview') | t }}
          </button>
        }
      </div>
      @if (markdown() && preview()) {
        <div class="preview" [innerHTML]="value()[active()] ?? '' | markdown"></div>
      }
    </div>
  `,
  styles: `
    :host {
      display: grid;
      gap: 0.4rem;
      min-width: 0;
    }
    .label {
      font-weight: 650;
    }
    .tabs {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
      border-bottom: 1px solid var(--fs-border);
    }
    .tab {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      margin-bottom: -1px;
      padding: 0.45rem 0.75rem;
      border: 1px solid transparent;
      border-radius: var(--fs-radius-sm) var(--fs-radius-sm) 0 0;
      background: none;
      color: var(--fs-text-muted);
      font: inherit;
      font-size: 0.88rem;
      font-weight: 600;
      cursor: pointer;
    }
    .tab--active {
      border-color: var(--fs-border);
      border-bottom-color: var(--fs-surface);
      background: var(--fs-surface);
      color: var(--fs-text);
    }
    .tab:focus-visible {
      outline: 2px solid var(--fs-primary);
      outline-offset: 1px;
    }
    .tab__state {
      font-size: 0.65rem;
    }
    .tab__state--ok {
      color: var(--fs-primary);
    }
    .tab__state--missing {
      color: var(--fs-danger);
      font-size: 0.45rem;
    }
    .panel {
      display: grid;
      gap: 0.35rem;
    }
    .panel input,
    .panel textarea {
      width: 100%;
    }
    .panel textarea {
      resize: vertical;
    }
    .foot {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      gap: 0.5rem;
      color: var(--fs-text-muted);
      font-size: 0.82rem;
    }
    .foot__missing {
      color: var(--fs-danger);
      font-weight: 600;
    }
    .preview-toggle {
      padding: 0;
      border: 0;
      background: none;
      color: var(--fs-primary-strong);
      font: inherit;
      font-weight: 650;
      cursor: pointer;
    }
    .preview {
      padding: 0.75rem 1rem;
      border: 1px dashed var(--fs-border-strong);
      border-radius: var(--fs-radius-sm);
    }
  `,
})
export class LanguageTabsEditorComponent implements ControlValueAccessor, Validator {
  readonly label = input<string | undefined>(undefined);
  readonly multiline = input(false);
  readonly rows = input(4);
  /** Markdown text: adds a preview. */
  readonly markdown = input(false);
  readonly requiredLanguages = input<readonly UiLanguage[]>(['de']);
  readonly testId = input('shared.languageTabs');

  protected readonly uid = `fs-lang-tabs-${nextId++}`;
  protected readonly languages = LANGUAGES;
  protected readonly active = signal<UiLanguage>('de');
  protected readonly value = signal<TranslatedText>({});
  protected readonly disabled = signal(false);
  protected readonly preview = signal(false);

  protected readonly missingRequired = computed(() =>
    this.requiredLanguages().filter((lang) => !this.filled(lang)),
  );
  protected readonly missingNames = computed(() => this.missingRequired().join(', ').toUpperCase());

  private onChange: (value: TranslatedText) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  protected filled(lang: UiLanguage): boolean {
    return !!this.value()[lang]?.trim();
  }

  /** Untranslated languages show the German text as a hint (it is the fallback when reading). */
  protected placeholder(lang: UiLanguage): string {
    return lang === 'de' ? '' : (this.value().de ?? '');
  }

  protected update(lang: UiLanguage, text: string): void {
    const next = { ...this.value(), [lang]: text };
    if (!text) {
      delete next[lang];
    }
    this.value.set(next);
    this.onChange(next);
  }

  writeValue(value: TranslatedText | null | undefined): void {
    this.value.set({ ...(value ?? {}) });
  }

  registerOnChange(fn: (value: TranslatedText) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  validate(_control: AbstractControl): ValidationErrors | null {
    const missing = this.missingRequired();
    return missing.length ? { languagesMissing: missing } : null;
  }
}
