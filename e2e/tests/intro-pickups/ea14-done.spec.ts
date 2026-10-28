import { acceptPendingConsents, expect, test } from '../../fixtures';
import { einabId } from './_intro';

/** EA14: after the date the guide marks the booking done; it counts towards the newcomer's progress. */
test.use({ seedReset: 'test' });

test('EA14 mark done counts towards progress @EA14', async ({ as, api, apiOf, clock }) => {
  // quiz@local books guide's SUPERMARKET on 4 Nov
  const quiz = await as('quiz');
  const quizApi = apiOf(quiz);
  await acceptPendingConsents(quizApi);
  await api.loginAs('guide');
  const detail = await api.getJson<{ slots: { id: string }[] }>(`/api/intro-pickups/${einabId(1)}`);
  await quizApi.postJson(`/api/intro-pickups/slots/${detail.slots[0].id}/book`);

  // two days later
  await clock.fixServer('2026-11-05T09:00:00Z');
  const guide = await as('guide');
  await guide.goto('/guide');
  await guide.getByTestId('introPickups.guide.showPast').click();
  const einab = guide.locator(`[data-testid="introPickups.guide.einab"][data-id="${einabId(1)}"]`);
  const slot = einab.locator('[data-testid="introPickups.slot"][data-status="BOOKED"]');
  await expect(slot).toContainText('Chiara');
  await slot.getByTestId('introPickups.slot.done').click();
  await expect(einab.locator('[data-testid="introPickups.slot"][data-status="DONE"]')).toHaveCount(1);

  await quiz.goto('/me/intro-pickups');
  await expect(quiz.getByTestId('introPickups.my.progress')).toHaveAttribute('data-done', '1');
  await expect(quiz.getByTestId('introPickups.my.pastRow')).toHaveAttribute('data-status', 'DONE');
  await expect(quiz.getByTestId('introPickups.my.counted')).toBeVisible();
});
