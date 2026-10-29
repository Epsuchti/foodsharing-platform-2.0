/**
 * Food share point seed (M6 B6 / plan-m7 Q6, docs/seed-ids.md `164-fsp-seed.yaml`). In zuerich-stadt: `01`
 * Wiedikon (manager bieb, photo), `02` Langstrasse (bieb, photo), `03` Oerlikon (guide, photo), `04` Hottingen (bot)
 * ACTIVE; `05` Altstetten INACTIVE (created and managed by foodsaver, waits for bot); `06` Enge deleted. `07`
 * Winterthur Bahnhof (bot) in Winterthur. Followers: foodsaver `01` (bell + mail) and `02` (bell), jumper `01`, `03`.
 * bot administers the Zürich FSP group `22`, so he activates FSPs and gets the FSP_ACTIVATE bells.
 */
export const FSPS = {
  wiedikon: '00000049-0001-4000-8000-000000000001',
  langstrasse: '00000049-0001-4000-8000-000000000002',
  oerlikon: '00000049-0001-4000-8000-000000000003',
  hottingen: '00000049-0001-4000-8000-000000000004',
  /** INACTIVE, created by foodsaver. */
  altstetten: '00000049-0001-4000-8000-000000000005',
  /** Deleted. */
  enge: '00000049-0001-4000-8000-000000000006',
  winterthur: '00000049-0001-4000-8000-000000000007',
} as const;

/** The active FSPs of zuerich-stadt (region tab and map). */
export const ACTIVE_ZURICH_FSPS = [FSPS.wiedikon, FSPS.langstrasse, FSPS.oerlikon, FSPS.hottingen] as const;
