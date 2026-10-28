import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Button } from '@openng/optimus-ui/button';

import { TPipe } from '../../../../core/i18n/t.pipe';
import { CsvDropzoneComponent, CsvFile } from '../../../../shared/csv-dropzone/csv-dropzone.component';
import {
  LanguageTabsEditorComponent,
  TranslatedText,
} from '../../../../shared/language-tabs-editor/language-tabs-editor.component';
import { PhoneInputComponent } from '../../../../shared/phone/phone-input.component';
import { RequiredMessageDialogComponent } from '../../../../shared/required-message-dialog/required-message-dialog.component';

/** M2 form building blocks: phone input, five-language editor, CSV drop zone, required-message dialog. */
@Component({
  selector: 'fs-gallery-inputs-section',
  imports: [
    Button,
    CsvDropzoneComponent,
    LanguageTabsEditorComponent,
    PhoneInputComponent,
    ReactiveFormsModule,
    RequiredMessageDialogComponent,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h2 class="group" id="g-inputs">{{ 'dev.gallery.m2Inputs' | t }}</h2>
    <div class="grid">
      <section class="specimen" data-testid="gallery.phoneInput.states">
        <header class="specimen__head"><code>phone input (valid / mobile hint / invalid)</code></header>
        <div class="stack">
          <fs-phone-input [formControl]="phoneValid" [mobile]="true" testId="gallery.phoneValid" />
          <fs-phone-input [formControl]="phoneLandline" [mobile]="true" testId="gallery.phoneLandline" />
          <fs-phone-input [formControl]="phoneInvalid" testId="gallery.phoneInvalid" />
        </div>
      </section>
      <section class="specimen" data-testid="gallery.languageTabs.default">
        <header class="specimen__head"><code>language tabs (fr missing)</code></header>
        <fs-language-tabs-editor
          [formControl]="titles"
          [label]="'dev.gallery.fieldTitle' | t"
          [requiredLanguages]="['de', 'fr']"
          testId="gallery.languageTabs"
        />
        <fs-language-tabs-editor
          [formControl]="bodies"
          [label]="'dev.gallery.fieldBody' | t"
          [multiline]="true"
          [markdown]="true"
          testId="gallery.languageTabsBody"
        />
      </section>
      <section class="specimen" data-testid="gallery.csvDropzone.default">
        <header class="specimen__head"><code>csv drop zone</code></header>
        <fs-csv-dropzone (fileChange)="csv.set($event)" testId="gallery.csv" />
        @if (csv(); as file) {
          <p><code>{{ file.name }}: {{ file.rows }}</code></p>
        }
      </section>
      <section class="specimen" data-testid="gallery.requiredMessage.trigger">
        <header class="specimen__head"><code>required message dialog</code></header>
        <p-button
          [label]="'dev.gallery.verifyDemo' | t"
          icon="pi pi-verified"
          (onClick)="dialogOpen.set(true)"
          data-testid="gallery.requiredMessage.open"
        />
        <fs-required-message-dialog
          [(visible)]="dialogOpen"
          [title]="'dev.gallery.verifyDemo' | t"
          [text]="'dev.gallery.verifyText' | t"
          (confirm)="dialogOpen.set(false)"
          testId="gallery.requiredMessage"
        />
      </section>
    </div>
  `,
  styleUrl: '../../pages/gallery-page.component.scss',
})
export class InputsGallerySection {
  protected readonly phoneValid = new FormControl<string | null>('+41791234567');
  protected readonly phoneLandline = new FormControl<string | null>('044 123 45 67');
  protected readonly phoneInvalid = new FormControl<string | null>('12-ab');
  protected readonly titles = new FormControl<TranslatedText>({
    de: 'Abholregeln',
    en: 'Pickup rules',
    gws: 'Abholregle',
  });
  protected readonly bodies = new FormControl<TranslatedText>({
    de: '**Pünktlich** sein und den Ausweis zeigen.',
  });
  protected readonly csv = signal<CsvFile | null>(null);
  protected readonly dialogOpen = signal(false);
}
