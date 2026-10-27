/**
 * Hand-written mirrors of the OpenAPI models the e2e helpers touch (server/src/main/resources/openapi/schemas).
 * Specs that need more models should add them here, keeping field names identical to the contract.
 */
export type Language = 'de' | 'en' | 'gws' | 'fr' | 'it';
export type UserRole = 'FOODSHARER' | 'FOODSAVER' | 'STORE_MANAGER' | 'AMBASSADOR' | 'ORGA';
export type Theme = 'LIGHT' | 'DARK' | 'SYSTEM';

export interface ApiError {
  code: string;
  message: string;
  traceId: string;
  retryable: boolean;
  retryAfterSeconds?: number;
  fieldErrors?: { field: string; code: string; message: string }[];
}

export interface AppInfo {
  version: string;
  devMode: boolean;
  languages: Language[];
  loginMethods: { google: boolean; magicLink: boolean; passkeys: boolean };
  serverTime: string;
}

export interface SessionUser {
  id: string;
  email: string;
  displayName: string;
  avatarUrl?: string;
  role: UserRole;
  language: Language;
  theme: Theme;
  verified: boolean;
}

export interface SessionInfo {
  authenticated: boolean;
  user?: SessionUser;
  permissions: string[];
  sessionId?: string;
}

export interface DevUser {
  id: string;
  email: string;
  displayName: string;
  role: UserRole;
  descriptionKey: string;
}

export interface DevClockState {
  now: string;
  fixed: boolean;
}

export interface CapturedMail {
  id: string;
  to: string;
  subject: string;
  text: string;
  html?: string;
  links: string[];
  sentAt: string;
}
