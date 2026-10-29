import avatar from '@openng/optimus-ui-themes/aura/avatar';
import badge from '@openng/optimus-ui-themes/aura/badge';
import base from '@openng/optimus-ui-themes/aura/base';
import button from '@openng/optimus-ui-themes/aura/button';
import card from '@openng/optimus-ui-themes/aura/card';
import checkbox from '@openng/optimus-ui-themes/aura/checkbox';
import chip from '@openng/optimus-ui-themes/aura/chip';
import confirmdialog from '@openng/optimus-ui-themes/aura/confirmdialog';
import css from '@openng/optimus-ui-themes/aura/css';
import datepicker from '@openng/optimus-ui-themes/aura/datepicker';
import dialog from '@openng/optimus-ui-themes/aura/dialog';
import divider from '@openng/optimus-ui-themes/aura/divider';
import drawer from '@openng/optimus-ui-themes/aura/drawer';
import editor from '@openng/optimus-ui-themes/aura/editor';
import iconfield from '@openng/optimus-ui-themes/aura/iconfield';
import inputnumber from '@openng/optimus-ui-themes/aura/inputnumber';
import inputtext from '@openng/optimus-ui-themes/aura/inputtext';
import message from '@openng/optimus-ui-themes/aura/message';
import multiselect from '@openng/optimus-ui-themes/aura/multiselect';
import overlaybadge from '@openng/optimus-ui-themes/aura/overlaybadge';
import paginator from '@openng/optimus-ui-themes/aura/paginator';
import popover from '@openng/optimus-ui-themes/aura/popover';
import progressbar from '@openng/optimus-ui-themes/aura/progressbar';
import progressspinner from '@openng/optimus-ui-themes/aura/progressspinner';
import radiobutton from '@openng/optimus-ui-themes/aura/radiobutton';
import ripple from '@openng/optimus-ui-themes/aura/ripple';
import select from '@openng/optimus-ui-themes/aura/select';
import selectbutton from '@openng/optimus-ui-themes/aura/selectbutton';
import skeleton from '@openng/optimus-ui-themes/aura/skeleton';
import tabs from '@openng/optimus-ui-themes/aura/tabs';
import tag from '@openng/optimus-ui-themes/aura/tag';
import textarea from '@openng/optimus-ui-themes/aura/textarea';
import timeline from '@openng/optimus-ui-themes/aura/timeline';
import toast from '@openng/optimus-ui-themes/aura/toast';
import togglebutton from '@openng/optimus-ui-themes/aura/togglebutton';
import toggleswitch from '@openng/optimus-ui-themes/aura/toggleswitch';
import tooltip from '@openng/optimus-ui-themes/aura/tooltip';
import virtualscroller from '@openng/optimus-ui-themes/aura/virtualscroller';
import type { ExtendedCSS } from '@openng/optimus-ui-themes/types';

/**
 * Component design tokens of the Optimus components this app uses (plus the ones they render inside: chip and
 * checkbox in multiselect, togglebutton in selectbutton, virtualscroller in select, …). The full Aura preset
 * imports the tokens of all ~90 components, and Optimus turns every one of them into CSS variables at start-up:
 * ~135 kB of script in the start-up bundle and the biggest chunk of start-up style work (D11.5.3, Lighthouse
 * `/onboarding`). `scripts/check-theme-tokens.mjs` (part of `npm run lint`) fails when a component is imported
 * from `@openng/optimus-ui/<name>` without its tokens here.
 */
export const AURA_COMPONENTS = {
  avatar,
  badge,
  button,
  card,
  checkbox,
  chip,
  confirmdialog,
  datepicker,
  dialog,
  divider,
  drawer,
  editor,
  iconfield,
  inputnumber,
  inputtext,
  message,
  multiselect,
  overlaybadge,
  paginator,
  popover,
  progressbar,
  progressspinner,
  radiobutton,
  ripple,
  select,
  selectbutton,
  skeleton,
  tabs,
  tag,
  textarea,
  timeline,
  toast,
  togglebutton,
  toggleswitch,
  tooltip,
  virtualscroller,
};

/** Aura (base tokens + css) with only AURA_COMPONENTS. The css module's typings name an export that its runtime has as default. */
export const LeanAura = { ...base, components: AURA_COMPONENTS, css: css as unknown as ExtendedCSS };
