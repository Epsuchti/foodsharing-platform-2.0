import { Type } from '@angular/core';

import { InputsGallerySection } from './sections/inputs.section';
import { JourneyGallerySection } from './sections/journey.section';
import { PeopleGallerySection } from './sections/people.section';
import { RegionsMapGallerySection } from './sections/regions-map.section';
import { ACCOUNT_GALLERY_SECTIONS } from './sections/account.section';
import { ADMIN_SETTINGS_GALLERY_SECTIONS } from './sections/admin-settings.section';
import { BASKETS_GALLERY_SECTIONS } from './sections/baskets.section';
import { CONTENT_GALLERY_SECTIONS } from './sections/content.section';
import { FOOD_SHARE_POINTS_GALLERY_SECTIONS } from './sections/food-share-points.section';
import { MAP_GALLERY_SECTIONS } from './sections/map.section';
import { GROUPS_GALLERY_SECTIONS } from './sections/groups.section';
import { INTRO_PICKUPS_GALLERY_SECTIONS } from './sections/intro-pickups.section';
import { LEGAL_GALLERY_SECTIONS } from './sections/legal.section';
import { ONBOARDING_GALLERY_SECTIONS } from './sections/onboarding.section';
import { PROFILES_GALLERY_SECTIONS } from './sections/profiles.section';
import { QUIZ_GALLERY_SECTIONS } from './sections/quiz.section';
import { REGIONS_GALLERY_SECTIONS } from './sections/regions.section';
import { REPORTS_GALLERY_SECTIONS } from './sections/reports.section';
import { SHARED_M6_GALLERY_SECTIONS } from './sections/shared-m6.section';
import { STORES_GALLERY_SECTIONS } from './sections/stores.section';
import { VERIFICATION_GALLERY_SECTIONS } from './sections/verification.section';

/**
 * Gallery sections rendered after the M1 specimens of /dev/gallery (M2 T17). Shared M2 components first, then one
 * list per feature module (`sections/<module>.section.ts`). Every `section[data-testid^="gallery."]` inside gets
 * a screenshot (e2e/screenshots/gallery.screen.ts).
 */
export const GALLERY_SECTIONS: readonly Type<unknown>[] = [
  JourneyGallerySection,
  RegionsMapGallerySection,
  InputsGallerySection,
  PeopleGallerySection,
  ...SHARED_M6_GALLERY_SECTIONS,
  ...CONTENT_GALLERY_SECTIONS,
  ...GROUPS_GALLERY_SECTIONS,
  ...INTRO_PICKUPS_GALLERY_SECTIONS,
  ...LEGAL_GALLERY_SECTIONS,
  ...ONBOARDING_GALLERY_SECTIONS,
  ...QUIZ_GALLERY_SECTIONS,
  ...REGIONS_GALLERY_SECTIONS,
  ...VERIFICATION_GALLERY_SECTIONS,
  ...STORES_GALLERY_SECTIONS,
  ...PROFILES_GALLERY_SECTIONS,
  ...ACCOUNT_GALLERY_SECTIONS,
  ...BASKETS_GALLERY_SECTIONS,
  ...FOOD_SHARE_POINTS_GALLERY_SECTIONS,
  ...REPORTS_GALLERY_SECTIONS,
  ...MAP_GALLERY_SECTIONS,
  ...ADMIN_SETTINGS_GALLERY_SECTIONS,
];
