import { CanMatchFn, UrlSegment } from '@angular/router';

import { LegalDocumentKind } from '../../api';

/** URL slugs of the legal documents (`/legal/privacy-policy`, …) ↔ `LegalDocumentKind`. */
export const LEGAL_SLUGS: Readonly<Record<LegalDocumentKind, string>> = {
  [LegalDocumentKind.PrivacyPolicy]: 'privacy-policy',
  [LegalDocumentKind.LegalAgreement]: 'legal-agreement',
  [LegalDocumentKind.ConfidentialityNotice]: 'confidentiality-notice',
};

export const LEGAL_KINDS: readonly LegalDocumentKind[] = [
  LegalDocumentKind.PrivacyPolicy,
  LegalDocumentKind.LegalAgreement,
  LegalDocumentKind.ConfidentialityNotice,
];

export const LEGAL_ICONS: Readonly<Record<LegalDocumentKind, string>> = {
  [LegalDocumentKind.PrivacyPolicy]: 'pi pi-shield',
  [LegalDocumentKind.LegalAgreement]: 'pi pi-file-check',
  [LegalDocumentKind.ConfidentialityNotice]: 'pi pi-lock',
};

/** `privacy-policy` or `PRIVACY_POLICY` → the kind; anything else → undefined. */
export function legalKindOf(slug: string | undefined): LegalDocumentKind | undefined {
  if (!slug) {
    return undefined;
  }
  const upper = slug.toUpperCase().replace(/-/g, '_');
  return LEGAL_KINDS.find((kind) => kind === upper);
}

export function legalSlug(kind: LegalDocumentKind): string {
  return LEGAL_SLUGS[kind];
}

/** Route matcher guard: segment `index` must be a known legal document (unknown → falls through to not-found). */
export function legalKindAt(index: number): CanMatchFn {
  return (_route, segments: UrlSegment[]) => legalKindOf(segments[index]?.path) !== undefined;
}
