import { Injectable, inject } from '@angular/core';

import { HistoryEntry } from '../../api';
import { I18nService } from '../../core/i18n/i18n.service';
import { MessageParams } from '../../core/i18n/message-format';
import { HistoryVisual } from './history-timeline.types';

/** Value shown for redacted fields (`@Tracked(redact = true)`). */
export const REDACTED_VALUE = '***';

/** Metadata keys shown as small facts below the label (everything else only in the details). */
const VISIBLE_FACTS = ['label', 'reason', 'count', 'template', 'language'] as const;

/** Icon + colour per action code; codes not listed fall back to suffix rules, then a neutral history icon. */
const VISUALS: Record<string, HistoryVisual> = {
  ACCOUNT_CREATED: { icon: 'pi pi-user-plus', tone: 'primary' },
  SETTINGS_CHANGED: { icon: 'pi pi-cog', tone: 'neutral' },
  FIELD_CHANGED: { icon: 'pi pi-pencil', tone: 'info' },
  ROLE_CHANGED: { icon: 'pi pi-star', tone: 'primary' },
  DEACTIVATED: { icon: 'pi pi-user-minus', tone: 'danger' },
  LOGIN: { icon: 'pi pi-sign-in', tone: 'primary' },
  LOGOUT: { icon: 'pi pi-sign-out', tone: 'neutral' },
  LOGIN_FAILED: { icon: 'pi pi-exclamation-triangle', tone: 'danger' },
  SESSION_REVOKED: { icon: 'pi pi-power-off', tone: 'warning' },
  SESSIONS_REVOKED_ALL: { icon: 'pi pi-power-off', tone: 'warning' },
  ACCOUNT_LINKED: { icon: 'pi pi-link', tone: 'info' },
  METHOD_REMOVED: { icon: 'pi pi-times-circle', tone: 'warning' },
  PASSKEY_REGISTERED: { icon: 'pi pi-key', tone: 'info' },
  PASSKEY_RENAMED: { icon: 'pi pi-key', tone: 'neutral' },
  PASSKEY_DELETED: { icon: 'pi pi-key', tone: 'warning' },
  MAGIC_LINK_REQUESTED: { icon: 'pi pi-envelope', tone: 'neutral' },
  REGISTRATION_BLOCKED: { icon: 'pi pi-ban', tone: 'danger' },
  MAIL_QUEUED: { icon: 'pi pi-inbox', tone: 'neutral' },
  MAIL_SENT: { icon: 'pi pi-send', tone: 'info' },
  MAIL_FAILED: { icon: 'pi pi-exclamation-circle', tone: 'danger' },
};

/** `SOME_NEW_ACTION` → `Some new action`, `displayName` → `Display name`. */
export function humanize(code: string | null | undefined): string {
  const words = (code ?? '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[_.-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words ? words[0].toUpperCase() + words.slice(1) : '';
}

export function historyActionKey(subjectType: string, action: string): string {
  return `history.action.${subjectType}.${action}`;
}

/** Icon and tone of an action (later modules add their codes to VISUALS or rely on the suffix rules). */
export function historyVisual(action: string): HistoryVisual {
  const known = VISUALS[action];
  if (known) {
    return known;
  }
  if (/(FAILED|BLOCKED|REJECTED|DECLINED)$/.test(action)) {
    return { icon: 'pi pi-exclamation-triangle', tone: 'danger' };
  }
  if (/(DELETED|REMOVED|LEFT|CANCELLED|WITHDRAWN)$/.test(action)) {
    return { icon: 'pi pi-minus-circle', tone: 'warning' };
  }
  if (/(CREATED|ADDED|APPROVED|ACCEPTED|JOINED|CONFIRMED)$/.test(action)) {
    return { icon: 'pi pi-plus-circle', tone: 'primary' };
  }
  if (/CHANGED$/.test(action)) {
    return { icon: 'pi pi-pencil', tone: 'info' };
  }
  return { icon: 'pi pi-history', tone: 'neutral' };
}

export interface HistoryFact {
  key: string;
  label: string;
  value: string;
}

export interface HistoryChange {
  field: string;
  oldValue: string;
  newValue: string;
}

/**
 * Turns history entries into translated text. Action labels live in `history.action.<SUBJECT>.<ACTION>`
 * (features/history/i18n, the key the backend reports as `labelKey`); they may use metadata and the raw
 * `field`/`oldValue`/`newValue` in ICU `select` blocks. Unknown actions fall back to a humanized code, so a new
 * backend action never shows a raw key.
 */
@Injectable({ providedIn: 'root' })
export class HistoryLabeler {
  private readonly i18n = inject(I18nService);

  /** Label of an action without entry context (filter options): `select` blocks take their `other` branch. */
  actionLabel(subjectType: string, action: string): string {
    const key = historyActionKey(subjectType, action);
    if (this.i18n.has(key)) {
      return this.i18n.t(key);
    }
    return this.i18n.t('shared.historyTimeline.unknownAction', { action: humanize(action) });
  }

  label(entry: HistoryEntry): string {
    const key = historyActionKey(entry.subjectType, entry.action);
    if (this.i18n.has(key)) {
      return this.i18n.t(key, this.params(entry));
    }
    return this.i18n.t('shared.historyTimeline.unknownAction', { action: humanize(entry.action) });
  }

  /** Params for the label: metadata values as strings plus field, oldValue, newValue (raw). */
  params(entry: HistoryEntry): MessageParams {
    const params: MessageParams = {};
    for (const [key, value] of Object.entries(entry.metadata ?? {})) {
      if (value !== null && value !== undefined) {
        params[key] = typeof value === 'object' ? JSON.stringify(value) : String(value);
      }
    }
    if (entry.field) params['field'] = entry.field;
    if (entry.oldValue !== undefined && entry.oldValue !== null) params['oldValue'] = entry.oldValue;
    if (entry.newValue !== undefined && entry.newValue !== null) params['newValue'] = entry.newValue;
    return params;
  }

  fieldLabel(field: string): string {
    const key = `shared.historyTimeline.field.${field}`;
    return this.i18n.has(key) ? this.i18n.t(key) : humanize(field);
  }

  /** Field values in words: roles, languages and themes translated, redacted and empty values marked. */
  value(field: string | undefined, value: string | null | undefined): string {
    if (value === null || value === undefined || value === '') {
      return this.i18n.t('shared.historyTimeline.noValue');
    }
    if (value === REDACTED_VALUE) {
      return this.i18n.t('shared.historyTimeline.redacted');
    }
    const lookup = this.valueKey(field, value);
    return lookup && this.i18n.has(lookup) ? this.i18n.t(lookup) : value;
  }

  change(entry: HistoryEntry): HistoryChange | null {
    if (!entry.field) {
      return null;
    }
    return {
      field: this.fieldLabel(entry.field),
      oldValue: this.value(entry.field, entry.oldValue),
      newValue: this.value(entry.field, entry.newValue),
    };
  }

  /** Short metadata facts worth showing without opening the details (passkey name, failure reason, …). */
  facts(entry: HistoryEntry): HistoryFact[] {
    const metadata = entry.metadata ?? {};
    const facts: HistoryFact[] = [];
    for (const key of VISIBLE_FACTS) {
      const raw = metadata[key];
      if (raw === null || raw === undefined || raw === '') {
        continue;
      }
      facts.push({ key, label: this.metaLabel(key), value: this.metaValue(key, raw) });
    }
    return facts;
  }

  /** All metadata as label/value pairs for the details panel. */
  metadata(entry: HistoryEntry): HistoryFact[] {
    return Object.entries(entry.metadata ?? {})
      .filter(([, raw]) => raw !== null && raw !== undefined && raw !== '')
      .map(([key, raw]) => ({ key, label: this.metaLabel(key), value: this.metaValue(key, raw) }));
  }

  metaLabel(key: string): string {
    const i18nKey = `shared.historyTimeline.meta.${key}`;
    return this.i18n.has(i18nKey) ? this.i18n.t(i18nKey) : humanize(key);
  }

  metaValue(key: string, raw: unknown): string {
    const value = typeof raw === 'object' ? JSON.stringify(raw) : String(raw);
    let lookup: string | null = null;
    switch (key) {
      case 'method':
      case 'provider':
        lookup = `shared.historyTimeline.method.${value}`;
        break;
      case 'reason': {
        // failure reasons are ApiErrorCodes, session-end reasons have their own labels
        const own = `shared.historyTimeline.reason.${value}`;
        lookup = this.i18n.has(own) ? own : `errors.${value}`;
        break;
      }
      case 'language':
        lookup = `lang.${value.toLowerCase()}`;
        break;
    }
    return lookup && this.i18n.has(lookup) ? this.i18n.t(lookup) : value;
  }

  private valueKey(field: string | undefined, value: string): string | null {
    switch (field) {
      case 'role':
        return `common.role.${value.toUpperCase()}`;
      case 'preferredLanguage':
      case 'language':
        return `lang.${value.toLowerCase()}`;
      case 'theme':
        return `theme.${value.toLowerCase()}`;
      default:
        return null;
    }
  }
}
