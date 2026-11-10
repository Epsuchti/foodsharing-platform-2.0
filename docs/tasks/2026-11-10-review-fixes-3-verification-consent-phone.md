# Review fixes 3/5: verification needs identity, consent text, phone in the profile (C1, A6, D2)

Package 3 of the 2026-11-10 review batch (order 1 → 2 → 3 → 4 → 5).

> **Q2, assumed, owner to confirm at plan approval:** "phone mandatory in the profile" means:
> - `/consent` no longer asks for it.
> - `/account/profile` cannot be saved without a valid mobile number, and the server refuses an own profile save that leaves `mobilePhone` empty (admin edits exempt).
> - Onboarding step 2 keeps requiring it (decision 44).
> - Existing users without a number get no wall: the existing dashboard hint (decision 162) and their next profile save ask for it.
> - Pickup eligibility stays per store (`requiresPhone`).

## Goal
Nobody can be verified before their identity is confirmed. A first-time user's consent page doesn't talk about "changed" documents. The phone number moves from the consent wall to the profile.

## Decisions
- C1 (resolves `todo-eric.md` "Should verification require a confirmed identity?" → **yes**, and the open question in decision 124): `VerificationService.verify` throws 409 `IDENTITY_NOT_CONFIRMED` when `identityConfirmedAt == null`. There is no ORGA exception. Unverify, revert identity and existing verified users are unchanged.
- A6: "first time" = no pending consent has an `acceptedVersion`. Mixed cases keep the "changed" text.
- D2 as in the box above. plan.md row "phone prompt" (line ~942, step 5 consent) is updated to "phone in step 2 and the profile".

## Scope
In: C1, A6, D2. Out: identity confirmation flow itself, wallet/passport, other consent page content, basket phone rules.

## Changes
**Contract**: `openapi/paths/verification.yaml` `verifyUser` gets `'409': IDENTITY_NOT_CONFIRMED` (next to ALREADY_VERIFIED). `openapi/paths/users.yaml` (own profile update): document 400 `VALIDATION_FAILED` field `mobilePhone`. Regenerate.

**Server**
- `verification/service/VerificationService.verify`: check identity after `ALREADY_VERIFIED`. Keep it as a pure rule `VerificationRules.mayVerify(User)` (new, static) so the unit test is direct.
- `user/service/UserService.applyProfile`: for `action == PROFILE_UPDATED` (own) only, when `change.mobilePhone() != null` it must be a valid, non-blank number. Otherwise 400 `VALIDATION_FAILED` with `ApiFieldError("mobilePhone", "NotBlank", …)`. Pure helper `ProfileRules.mobileOk(own, value)`. The profile form always sends the field, so a user without a number can't save it. Saves that leave the field out (null) still work. Admin path (`PROFILE_UPDATED_BY_ADMIN`) unchanged.
- Check all own-profile writers (onboarding step page, account profile, avatar/contact-visibility saves, if they go through `applyProfile`). Each must either send a valid number or leave the field out. Add a test per writer.

**Client**
- `features/verification/components/verification-candidate-card.component.ts`: "Verifizieren" disabled while `!c.identityConfirmedAt`, with the existing hint reworded to `verification.queue.identityRequired` ("Bestätige zuerst die Identität."). Same in `pages/user-verification-history-page.component.ts`. Map 409 `IDENTITY_NOT_CONFIRMED` in `errors.*` (5 languages).
- `features/legal/pages/consent-page.component.ts`: subtitle `legal.consent.subtitleFirst` ("Bevor du startest: Bitte lies diese Dokumente und stimme ihnen zu.") when first time. Remove `askPhone`/`phone`/`phoneOk`, the phone section, the profile call and the `legal.consent.phone*` keys.
- `features/account/pages/profile-page.component.{ts,html}`: `mobilePhone` gets `Validators.required` plus the existing phone validity, a required marker and the error `account.profile.mobileRequired`. Save is disabled while it is invalid.
- New keys in 5 languages; `npm run i18n:check`.

**Docs**: `docs/decisions.md` new row (C1 + D2, amends 124 and the EA4 phone prompt); `todo-eric.md` strike the identity question; `plan.md` phone row; `docs/ledger.md` Ve2/EA4 notes.

## UI
Visible: verify button state, consent subtitle and the missing phone block, required phone in the profile. No mockup.

## Tests
- New unit: `VerificationRules.mayVerify` (with/without identity); `ProfileRules.requiresMobile` (own clear → refused, admin clear → allowed, partial save without phone → allowed).
- `VerificationApiIT`: verify without identity → 409; with identity → 200. `UserProfileServiceTest`: the own-profile cases.
- Changed on purpose: every IT/e2e that verifies a user without confirming identity first (`VerificationApiIT`, `ve2-verify.spec.ts`, `verification-error-states.spec.ts`, persona `ambassador.spec.ts`, helpers that call `verifyUser`). They confirm identity first; none are skipped. The consent e2e/spec asserting the phone field. The profile spec.
- E2E `@Ve2` (button disabled until identity confirmed) and `@EA4` (new user's consent text, no phone). Screenshot states: queue card before/after identity, consent first time, profile phone error.

## Done when
- [ ] Server refuses verify without identity; UI shows why
- [ ] Consent: first-time text, no phone field
- [ ] Own profile can't be saved without a valid mobile; partial saves still work
- [ ] Decisions, todo-eric, plan, ledger updated; all tests green

## Risks
- Seed and test helpers that verify directly through the API. Fix them by confirming identity first; seed rows (direct SQL) are unaffected.
- Existing users without a number hit the rule only when saving the profile form. Communicate it in the profile error text.
