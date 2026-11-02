# Fairteiler cleaning shifts: follow-ups (decision 148 → 154, 155)

## Goal
Finish the items left out of FS2: cleaning shifts show up wherever a person's pickups do, reminders and automatic confirmation work, and the account data and time zone are correct.

## Owner answers (2026-11-02)
- Split into two PRs.
- When an account is deleted, future sign-ups are removed and past ones stay with the anonymised user.
- Add a time zone per Fairteiler.

## Part 1: where the shifts appear (decision 154, branch `feat/fsp-cleaning-agenda`)
- `GET /api/users/me/cleaning-shifts` and `AgendaDay.cleaningShifts`.
- "My pickups" and the dashboard card merge pickups and shifts by time; the agenda lists the shifts of the day.
- iCal: `cleaningShifts` switch, default on, one hour long.
- Counts are **separate numbers**: `ProfileStats.cleaningShiftCount` and `RegionStatistics.cleaningShiftsLastMonth`. Pickup counts drive kg saved, achievements and the pickup-count grouping, so a shift must not raise them.

## Part 2: behaviour (decision 155, branch `feat/fsp-cleaning-followups`, on top of part 1)
- Reminder job for confirmed sign-ups, same timing and user switch as pickups.
- Automatic confirmation: a switch per Fairteiler. The pickup policy belongs to a store and its rules (gap, cleaning rule, excluded dates) are about store pickups, so it can't read a Fairteiler sign-up.
- Account deletion and data export.
- `food_share_point.time_zone`, taken from the region when a Fairteiler is created. Changing it moves the regular shifts' local time onto the new zone; existing sign-ups keep their instant.
- A test that the decline text with several cleaning Fairteiler stays within the mail and Telegram limits.

## Tests
- `FspCleaningFlowIT`, `CalendarFeedServiceTest`, `pickups.util.spec.ts`, `e2e/tests/fsp/fs2-cleaning-agenda.spec.ts @FS2`, a screenshot of "My pickups" with a shift. Baselines of screens that now show the new tiles are retaken deliberately.

## Done when
- [ ] Both PRs pass CI apart from the checks that also fail without them.
- [ ] `todo.md` no longer lists these items.
