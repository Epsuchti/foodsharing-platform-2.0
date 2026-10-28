import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Language, LegalDocument, LegalDocumentKind, PublicLegalService } from '../../../api';
import { loadResource } from '../../../core/data/data-state';
import { I18nService } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { DATA_STATE_IMPORTS } from '../../../shared/data-state';
import { PageHeaderComponent } from '../../../shared/page-header/page-header.component';
import { LegalDocumentViewComponent } from '../components/legal-document-view.component';
import { LEGAL_ICONS, LEGAL_KINDS, legalKindOf, legalSlug } from '../legal-kind';

/** /legal/:kind (Ve1, public): the current version of a legal document in the UI language, links to the others. */
@Component({
  selector: 'fs-legal-document-page',
  imports: [
    ...DATA_STATE_IMPORTS,
    LegalDocumentViewComponent,
    PageHeaderComponent,
    RouterLink,
    RouterLinkActive,
    TPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fs-page-header
      [eyebrow]="'legal.document.eyebrow' | t"
      [title]="'legal.kind.' + legalKind() | t"
      [icon]="icons[legalKind()]"
    />
    <nav class="others" [attr.aria-label]="'legal.document.others' | t">
      @for (kind of kinds; track kind) {
        <a
          class="others__link"
          [routerLink]="['/legal', slug(kind)]"
          routerLinkActive="others__link--active"
          ariaCurrentWhenActive="page"
          [attr.data-testid]="'legal.document.link.' + kind"
        >
          <i [class]="icons[kind]" aria-hidden="true"></i>
          {{ 'legal.kind.' + kind | t }}
        </a>
      }
    </nav>
    <fs-data-state
      [state]="document"
      skeleton="detail"
      backLink="/"
      data-testid="legal.document.state"
    >
      <ng-template [fsData]="document" let-doc>
        <fs-legal-document-view [document]="doc" />
      </ng-template>
    </fs-data-state>
  `,
  styles: `
    :host {
      display: block;
      width: min(100%, 52rem);
      margin: 0 auto;
    }
    .others {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 1.25rem;
    }
    .others__link {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.45rem 0.85rem;
      border: 1px solid var(--fs-border);
      border-radius: 999px;
      color: var(--fs-text);
      font-size: 0.88rem;
      font-weight: 600;
      text-decoration: none;
    }
    .others__link:hover,
    .others__link:focus-visible {
      border-color: var(--fs-primary);
    }
    .others__link--active {
      border-color: var(--fs-primary);
      background: var(--fs-primary-soft);
      color: var(--fs-primary-strong);
    }
  `,
})
export class LegalDocumentPageComponent {
  private readonly api = inject(PublicLegalService);
  private readonly i18n = inject(I18nService);

  /** Route parameter (`privacy-policy`, …; the route only matches known kinds). */
  readonly kind = input.required<string>();

  protected readonly kinds = LEGAL_KINDS;
  protected readonly icons = LEGAL_ICONS;
  protected readonly legalKind = computed(
    () => legalKindOf(this.kind()) ?? LegalDocumentKind.PrivacyPolicy,
  );

  protected readonly document = loadResource<
    LegalDocument,
    { kind: LegalDocumentKind; lang: string }
  >({
    params: () => ({ kind: this.legalKind(), lang: this.i18n.language() }),
    loader: ({ kind, lang }) => this.api.getLegalDocument({ kind, lang: lang as Language }),
  });

  protected slug(kind: LegalDocumentKind): string {
    return legalSlug(kind);
  }
}
