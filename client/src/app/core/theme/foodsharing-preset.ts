import { definePreset } from '@openng/optimus-ui-themes';
import Aura from '@openng/optimus-ui-themes/aura';

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
  500: '#6d7b69',
  600: '#535f50',
  700: '#3d473b',
  800: '#283027',
  900: '#1a201a',
  950: '#121712',
} as const;

/**
 * Optimus/Aura preset with the foodsharing palette. Dark mode is switched by the `.fs-dark` class on <html>
 * (ThemeService); see app.config.ts for the provideOptimus options.
 */
export const FoodsharingPreset = definePreset(Aura, {
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
    toast: {
      root: {
        borderRadius: '1rem',
      },
    },
  },
});
