import { FeatureToggleKey } from '../../api';

/** Value types of configuration keys (server `ConfigurationKey.Type`). */
export type ConfigurationType = 'STRING' | 'INTEGER' | 'BOOLEAN' | 'URL' | 'EMAIL' | 'LIST';

/**
 * Types of the known configuration keys, copied from the server enum `admin.domain.ConfigurationKey` until
 * `ConfigurationEntry` carries its type (backend request). Unknown keys are edited as plain text; the server
 * validates every value (400 CONFIGURATION_VALUE_INVALID).
 */
export const CONFIGURATION_TYPES: Readonly<Record<string, ConfigurationType>> = {
  'support.email': 'EMAIL',
  'petition.url': 'URL',
  'petition.signatures': 'INTEGER',
  'petition.days-left': 'INTEGER',
  'donation.show-campaign-card': 'BOOLEAN',
  'donation.show-modal': 'BOOLEAN',
  'donation.modal-hours-logged-in': 'INTEGER',
  'donation.modal-hours-logged-out': 'INTEGER',
  'donation.info-url': 'URL',
  'donation.iframe-one-time-url': 'URL',
  'donation.iframe-campaign-url': 'URL',
  'donation.iframe-friendship-circle-url': 'URL',
  'donation.iframe-self-service-url': 'URL',
  'donation.campaign-page-slugs': 'LIST',
  'statistics.public-cache-minutes': 'INTEGER',
  'oauth.issuer': 'URL',
};

export function configurationType(key: string): ConfigurationType {
  return CONFIGURATION_TYPES[key] ?? 'STRING';
}

/** i18n key suffix of a configuration key: `support.email` → `support_email` (catalog keys are flat). */
export function configurationLabelKey(key: string): string {
  return `adminSettings.configuration.key.${key.replace(/[.-]/g, '_')}`;
}

/** Section of a configuration key for grouping: the part before the first dot. */
export function configurationGroup(key: string): string {
  const dot = key.indexOf('.');
  return dot > 0 ? key.slice(0, dot) : key;
}

/** Display order of the toggles: the order of the generated enum (unknown keys go last). */
export function toggleOrder(key: string): number {
  const index = (Object.values(FeatureToggleKey) as string[]).indexOf(key);
  return index < 0 ? Number.MAX_SAFE_INTEGER : index;
}

/** Human file size: 1023 B, 1.5 KB, 12.3 MB (binary units). */
export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 1024) {
    return `${Math.max(0, Math.round(bytes || 0))} B`;
  }
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value >= 100 ? Math.round(value) : value.toFixed(1)} ${units[unit]}`;
}

/** Image uploads get a preview through the `sm` variant (A41); other files show an icon. */
export function isImage(mimeType: string | undefined): boolean {
  return !!mimeType && mimeType.startsWith('image/');
}

/** Subject id of SYSTEM history entries (server `HistoryService.SYSTEM_SUBJECT_ID`, the nil UUID). */
export const SYSTEM_SUBJECT_ID = '00000000-0000-0000-0000-000000000000';

/** Minutes of the IP block duration presets (empty = permanent). */
export const IP_BLOCK_DURATIONS: readonly (number | null)[] = [60, 60 * 24, 60 * 24 * 7, 60 * 24 * 30, null];
