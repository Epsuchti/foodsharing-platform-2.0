import {
  CONVERSATIONS,
  bubbles,
  expect,
  expectDataState,
  expectNoSeriousViolations,
  expectStatus,
  listItem,
  realtimeEventTypes,
  sendMessage,
  test,
  waitForRealtime,
} from '../../fixtures';

/** N2 realtime (A32): STOMP at /ws, cookie-authenticated, `/user/queue/events`, refetch after reconnect. */
test.use({ seedReset: 'test' });

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('N2 message sent in one browser context appears in another within 2 s without reload @N2 @N1 @page', async ({ page, as, api, dev }) => {
    await page.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await waitForRealtime(page);
    const me = await dev.user('foodsaver');
    const sessions = await api.getJson<{ userIds: string[] }>('/api/dev/realtime/sessions');
    expect(sessions.userIds).toContain(me.id);

    const bieb = await as('bieb');
    await bieb.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
    await expectDataState(bieb, 'messaging.conversation.state', 'success');
    await waitForRealtime(bieb);
    const before = await bubbles(page).count();

    await bieb.getByTestId('messaging.conversation.composer').fill('Bist du heute Abend dabei?');
    await bieb.getByTestId('messaging.conversation.send').click();
    await expect(bubbles(page)).toHaveCount(before + 1, { timeout: 2_000 });
    await expect(bubbles(page).last()).toContainText('heute Abend');
    await expect(bubbles(page).last()).toHaveAttribute('data-own', 'false');
    await expect(listItem(page, CONVERSATIONS.foodsaverBieb).getByTestId('messaging.list.preview')).toContainText('heute Abend');
    // the open conversation is read right away: no badge
    await expect(listItem(page, CONVERSATIONS.foodsaverBieb).getByTestId('messaging.list.unread')).toHaveCount(0);

    // and back
    await page.getByTestId('messaging.conversation.composer').fill('Ja, bis später!');
    await page.getByTestId('messaging.conversation.composer').press('Enter');
    await expect(bubbles(bieb).last()).toContainText('bis später', { timeout: 2_000 });
    await expectNoSeriousViolations(page);
  });

  test('N2 after going offline and back online the client reconnects and fetches missed messages @N2', async ({ page, context, as, apiOf }) => {
    await page.goto(`/messages/${CONVERSATIONS.foodsaverBieb}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await waitForRealtime(page);
    const before = await bubbles(page).count();
    const biebApi = apiOf(await as('bieb'));

    await context.setOffline(true);
    await expect
      .poll(() => page.evaluate(() => (window as unknown as { __fsRealtime: { connected(): boolean } }).__fsRealtime.connected()))
      .toBe(false);
    for (const body of ['Eins', 'Zwei', 'Drei']) {
      await sendMessage(biebApi, CONVERSATIONS.foodsaverBieb, body);
    }
    await context.setOffline(false);
    await waitForRealtime(page);
    await expect(bubbles(page)).toHaveCount(before + 3);
    const lastThree = (await bubbles(page).getByTestId('messaging.conversation.body').allTextContents()).slice(-3);
    expect(lastThree.map((text) => text.trim())).toEqual(['Eins', 'Zwei', 'Drei']);
  });

  test('N2 a dropped connection reconnects with backoff and refetches @N2', async ({ page, as, apiOf }) => {
    await page.goto(`/messages/${CONVERSATIONS.kochgruppe}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await waitForRealtime(page);
    const before = await bubbles(page).count();
    await page.evaluate(() => (window as unknown as { __fsRealtime: { drop(): void } }).__fsRealtime.drop());
    await sendMessage(apiOf(await as('guide')), CONVERSATIONS.kochgruppe, 'Während der Verbindungspause');
    await waitForRealtime(page);
    await expect(bubbles(page)).toHaveCount(before + 1);
    await expect(bubbles(page).last()).toContainText('Verbindungspause');
  });
});

test.describe('guide', () => {
  test.use({ role: 'guide' });

  test('N2 new bell arrives live @N2 @N3', async ({ page, api, as, apiOf, dev, store }) => {
    await page.goto('/dashboard');
    await waitForRealtime(page);
    const guide = await dev.user('guide');
    const s01 = store.id('S-01');
    await expectStatus(await api.post(`/api/stores/${s01}/applications`, { text: 'Ich habe abends Zeit.' }), [200, 201, 204], 'apply');
    await expectStatus(await apiOf(await as('bieb')).post(`/api/stores/${s01}/applications/${guide.id}/accept`), [200, 201, 204], 'accept');
    await expect.poll(() => realtimeEventTypes(page), { timeout: 5_000 }).toContain('BELL');
  });
});

test.describe('anonymous', () => {
  test('N2 unauthenticated handshake is refused @N2', async ({ page }) => {
    await page.goto('/');
    const result = await page.evaluate(
      () =>
        new Promise<string>((resolve) => {
          const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`);
          socket.onopen = () => {
            socket.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\0');
          };
          socket.onmessage = (event) => resolve(String(event.data).split('\n')[0]);
          socket.onerror = () => resolve('error');
          socket.onclose = () => resolve('closed');
          setTimeout(() => resolve('timeout'), 5_000);
        }),
    );
    expect(['error', 'closed', 'ERROR']).toContain(result);
  });
});
