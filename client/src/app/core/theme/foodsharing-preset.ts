import { definePreset } from '@openng/optimus-ui-themes';
import { LeanAura } from './aura-lean';

/** foodsharing green (from foodsharing-einab-tabelle). */
export const PRIMARY_PALETTE = {
  50: '#effaf2',
  100: '#d8f3df',
  200: '#b4e7c2',
  300: '#82d59b',
  400: '#4cba70',
  500: '#25834a',
  600: '#1e713f',
  700: '#195d35',
  800: '#174b2d',
  900: '#123d25',
  950: '#082116',
} as const;

/** Warm, slightly green-tinted neutrals instead of Aura's slate/zinc. */
const SURFACE_PALETTE = {
  0: '#ffffff',
  50: '#f6f8f4',
  100: '#edf1ea',
  200: '#dde5d9',
  300: '#c4d0bf',
  400: '#98a693',
  // Aura's muted text and secondary buttons use 500: ≥ 4.5:1 on white and on surface.100 (WCAG AA).
  500: '#5f6d5b',
  600: '#535f50',
  700: '#3d473b',
  800: '#283027',
  900: '#1a201a',
  950: '#121712',
} as const;

/**
 * Aura's severity colours (500 shades) fail WCAG AA contrast with white text and as text on white. These
 * overrides move solid buttons and coloured text one notch darker in light mode and lighter in dark mode.
 */
const SEVERITY_HUES = {
  info: 'sky',
  success: 'green',
  warn: 'orange',
  help: 'purple',
  danger: 'red',
} as const;
type Severity = keyof typeof SEVERITY_HUES;

function bySeverity<T>(build: (hue: string) => T): Record<Severity, T> {
  return Object.fromEntries(
    Object.entries(SEVERITY_HUES).map(([severity, hue]) => [severity, build(hue)]),
  ) as Record<Severity, T>;
}

const solidButton = (hue: string) => ({
  background: `{${hue}.700}`,
  hoverBackground: `{${hue}.800}`,
  activeBackground: `{${hue}.900}`,
  borderColor: `{${hue}.700}`,
  hoverBorderColor: `{${hue}.800}`,
  activeBorderColor: `{${hue}.900}`,
  color: '#ffffff',
  hoverColor: '#ffffff',
  activeColor: '#ffffff',
  focusRing: { color: `{${hue}.700}`, shadow: 'none' },
});

const MESSAGE_HUES = { info: 'blue', success: 'green', warn: 'yellow', error: 'red' } as const;

function messageColors(shade: number) {
  return Object.fromEntries(
    Object.entries(MESSAGE_HUES).map(([severity, hue]) => [
      severity,
      {
        color: `{${hue}.${shade}}`,
        outlined: { color: `{${hue}.${shade}}`, borderColor: `{${hue}.${shade}}` },
        simple: { color: `{${hue}.${shade}}` },
      },
    ]),
  );
}

function toastColors(shade: number) {
  return Object.fromEntries(
    Object.entries(MESSAGE_HUES).map(([severity, hue]) => [
      severity,
      { color: `{${hue}.${shade}}` },
    ]),
  );
}

/**
 * Optimus/Aura preset with the foodsharing palette. Dark mode is switched by the `.fs-dark` class on <html>
 * (ThemeService); see app.config.ts for the provideOptimus options.
 */
export const FoodsharingPreset = definePreset(LeanAura, {
  semantic: {
    primary: PRIMARY_PALETTE,
    focusRing: {
      width: '3px',
      style: 'solid',
      color: 'color-mix(in srgb, {primary.500} 45%, transparent)',
      offset: '2px',
    },
    formField: {
      borderRadius: '0.75rem',
    },
    colorScheme: {
      light: {
        surface: SURFACE_PALETTE,
        primary: {
          color: '{primary.500}',
          contrastColor: '#ffffff',
          hoverColor: '{primary.600}',
          activeColor: '{primary.700}',
        },
        highlight: {
          background: '{primary.50}',
          focusBackground: '{primary.100}',
          color: '{primary.700}',
          focusColor: '{primary.800}',
        },
      },
      dark: {
        surface: SURFACE_PALETTE,
        primary: {
          color: '{primary.400}',
          contrastColor: '{surface.950}',
          hoverColor: '{primary.300}',
          activeColor: '{primary.200}',
        },
        highlight: {
          background: 'color-mix(in srgb, {primary.400} 16%, transparent)',
          focusBackground: 'color-mix(in srgb, {primary.400} 24%, transparent)',
          color: 'rgba(255,255,255,.87)',
          focusColor: 'rgba(255,255,255,.87)',
        },
      },
    },
  },
  components: {
    button: {
      root: {
        borderRadius: '0.75rem',
        paddingY: '0.625rem',
        paddingX: '1.125rem',
      },
      colorScheme: {
        light: {
          root: bySeverity(solidButton),
          outlined: {
            ...bySeverity((hue) => ({ color: `{${hue}.700}`, borderColor: `{${hue}.300}` })),
            // primary.500 on the hover/active tint (primary.50/100) is just below 4.5:1
            primary: { color: '{primary.600}' },
          },
          text: {
            ...bySeverity((hue) => ({ color: `{${hue}.700}` })),
            primary: { color: '{primary.600}' },
          },
        },
        dark: {
          outlined: bySeverity((hue) => ({ color: `{${hue}.300}` })),
          text: bySeverity((hue) => ({ color: `{${hue}.300}` })),
        },
      },
    },
    message: {
      colorScheme: {
        light: messageColors(800),
        dark: messageColors(300),
      },
    },
    toast: {
      root: {
        borderRadius: '1rem',
      },
      colorScheme: {
        light: toastColors(800),
        dark: toastColors(300),
      },
    },
    card: {
      root: {
        borderRadius: '1.25rem',
      },
    },
    dialog: {
      root: {
        borderRadius: '1.25rem',
      },
    },
  },
});
