import {
  CONVERSATIONS,
  bubble,
  bubbles,
  expect,
  expectDataState,
  expectErrorState,
  expectNoSeriousViolations,
  expectStatus,
  getConversation,
  injectApiError,
  listItem,
  lookupConversation,
  sendMessage,
  test,
  waitForRealtime,
} from '../../fixtures';

/**
 * N1 conversations (A33). Seed: foodsaver ↔ bieb direct (2 unread for foodsaver), foodsaver ↔ bot, group
 * "Kochgruppe Planung" (foodsaver, jumper, guide), S-01 team/jumper chats (locked).
 */
test.use({ seedReset: 'test' });

test.describe('foodsaver', () => {
  test.use({ role: 'foodsaver' });

  test('N1 lookup creates a direct conversation once and lists it with unread count @N1 @page', async ({ page, api, dev }) => {
    const guide = await dev.user('guide');
    await page.goto('/messages');
    await expectDataState(page, 'messaging.list.state', 'success');
    await expect(listItem(page, CONVERSATIONS.foodsaverBieb).getByTestId('messaging.list.unread')).toHaveText('2');
    await expect(listItem(page, CONVERSATIONS.kochgruppe)).toBeVisible();

    await page.getByTestId('messaging.list.new').click();
    await page.getByTestId('messaging.new.picker.search').fill(guide.displayName.slice(0, 4));
    await page.locator(`[data-testid="messaging.new.picker.hit"][data-user-id="${guide.id}"]`).getByTestId('messaging.new.picker.add').click();
    await expect(page.getByTestId('messaging.new.recipient')).toHaveCount(1);
    await page.getByTestId('messaging.new.start').click();
    await expect(page).toHaveURL(/\/messages\/[0-9a-f-]{36}$/);
    const conversationId = page.url().split('/').pop()!;
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText(guide.displayName);
    await expect(page.getByTestId('messaging.conversation.empty')).toBeVisible();

    await page.getByTestId('messaging.conversation.composer').fill('Hallo Julien, hast du am Donnerstag Zeit?');
    await page.getByTestId('messaging.conversation.composer').press('Enter');
    await expect(bubbles(page).last()).toContainText('Donnerstag');
    await expect(bubbles(page).last()).toHaveAttribute('data-own', 'true');
    await expect(page.getByTestId('messaging.conversation.pending')).toHaveCount(0);
    await expect(listItem(page, conversationId).getByTestId('messaging.list.preview')).toContainText('Donnerstag');

    // the same members again: the existing conversation is reused
    const again = await lookupConversation(api, [guide.id]);
    expect(again.id).toBe(conversationId);
    await expect(page.getByTestId('messaging.list.item')).toHaveCount(6);
    await expectNoSeriousViolations(page);
  });

  test('N1 group conversation: create, rename, add and leave member; locked store chat cannot be renamed @N1 @S10', async ({ page, dev, as, apiOf }) => {
    const [jumper, guide, bieb] = await Promise.all([dev.user('jumper'), dev.user('guide'), dev.user('bieb')]);
    await page.goto('/messages/new');
    await expect(page.getByRole('dialog')).toBeVisible();
    for (const user of [jumper, guide]) {
      await page.getByTestId('messaging.new.picker.search').fill(user.displayName.slice(0, 4));
      await page.locator(`[data-testid="messaging.new.picker.hit"][data-user-id="${user.id}"]`).getByTestId('messaging.new.picker.add').click();
    }
    await page.getByTestId('messaging.new.name').fill('Velokurier Planung');
    await page.getByTestId('messaging.new.start').click();
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText('Velokurier Planung');
    const conversationId = page.url().split('/').pop()!;

    await page.getByTestId('messaging.conversation.rename').click();
    await page.getByTestId('messaging.conversation.renameInput').fill('Velokurier Team');
    await page.getByTestId('messaging.conversation.renameSave').click();
    await expect(page.getByTestId('messaging.conversation.name')).toHaveText('Velokurier Team');

    await page.getByTestId('messaging.conversation.members').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('messaging.conversation.member')).toHaveCount(3);
    await dialog.getByTestId('messaging.conversation.addMember.search').fill(bieb.displayName.slice(0, 4));
    await dialog.locator(`[data-testid="messaging.conversation.addMember.hit"][data-user-id="${bieb.id}"]`).getByTestId('messaging.conversation.addMember.add').click();
    await expect(dialog.getByTestId('messaging.conversation.member')).toHaveCount(4);
    await page.keyboard.press('Escape');

    await page.getByTestId('messaging.conversation.leave').click();
    await page.locator('.p-confirmdialog-accept-button').click();
    await expect(page).toHaveURL(/\/messages$/);
    await expect(listItem(page, conversationId)).toHaveCount(0);

    // locked store team chat: no rename, no leave
    const biebPage = await as('bieb');
    await biebPage.goto(`/messages/${CONVERSATIONS.s01Team}`);
    await expectDataState(biebPage, 'messaging.conversation.state', 'success');
    await expect(biebPage.getByTestId('messaging.conversation.header')).toBeVisible();
    await expect(biebPage.getByTestId('messaging.conversation.rename')).toHaveCount(0);
    await expect(biebPage.getByTestId('messaging.conversation.leave')).toHaveCount(0);
    const locked = await apiOf(biebPage).fetch('PATCH', `/api/conversations/${CONVERSATIONS.s01Team}`, { name: 'Neu' });
    expect(locked.status()).toBeGreaterThanOrEqual(400);
  });

  test('N1 mark read clears badge, mentions render as name links to the profile @N1', async ({ page, api, dev, apiOf, as }) => {
    const me = await dev.user('foodsaver');
    const biebPage = await as('bieb');
    const biebApi = apiOf(biebPage);
    await sendMessage(biebApi, CONVERSATIONS.foodsaverBieb, `Danke @[${me.displayName}](user:${me.id}) für https://foodsharing.network/info!`);

    await page.goto('/messages');
    const item = listItem(page, CONVERSATIONS.foodsaverBieb);
    await expect(item.getByTestId('messaging.list.unread')).toHaveText('3');
    await item.click();
    await expectDataState(page, 'messaging.conversation.state', 'success');
    const last = bubbles(page).last();
    const mention = last.getByTestId('messaging.conversation.mention');
    await expect(mention).toHaveText(`@${me.displayName}`);
    await expect(mention).toHaveAttribute('href', `/profile/${me.id}`);
    // the viewer is the one mentioned: ring
    await expect(mention).toHaveClass(/fs-mention--me/);
    await expect(last.getByRole('link', { name: 'https://foodsharing.network/info' })).toHaveAttribute('rel', /noopener/);
    await expect(item.getByTestId('messaging.list.unread')).toHaveCount(0);
    await expect.poll(async () => (await getConversation(api, CONVERSATIONS.foodsaverBieb)).unreadCount).toBe(0);

    // mention autocomplete inserts the plain @Name (decision 162); the token is built on send and the sent bubble
    // renders the @Name pill (not the viewer: no ring)
    const bieb = await dev.user('bieb');
    await page.getByTestId('messaging.conversation.composer').fill('Super, @');
    await expect(page.getByTestId('messaging.conversation.mentionPick')).toHaveCount(1);
    await page.getByTestId('messaging.conversation.mentionPick').click();
    // the name reaches the textarea with the next change detection; typing before that appended to the old text
    // and the next input event overwrote it ("Super, @bis bald" under load)
    await expect(page.getByTestId('messaging.conversation.composer')).toHaveValue(`Super, @${bieb.displayName} `);
    await page.getByTestId('messaging.conversation.composer').press('End');
    await page.getByTestId('messaging.conversation.composer').pressSequentially('bis bald');
    await page.getByTestId('messaging.conversation.send').click();
    await expect(bubbles(page).last().getByTestId('messaging.conversation.mention')).toHaveAttribute('href', `/profile/${bieb.id}`);
    await expect(bubbles(page).last().getByTestId('messaging.conversation.mention')).not.toHaveClass(/fs-mention--me/);
    await expect(bubbles(page).last()).toContainText('bis bald');
    const lastBody = async () =>
      (await api.getJson<{ messages: { body: string }[] }>(`/api/conversations/${CONVERSATIONS.foodsaverBieb}`)).messages.at(-1)?.body;
    await expect.poll(lastBody).toBe(
      `Super, @[${bieb.displayName}](user:${bieb.id}) bis bald`,
    );
  });

  test('N1 nav badge counts conversations with unread messages, updates live and clears on read @N1 @N2', async ({ page, api, as, apiOf }) => {
    const unread = async () => (await api.getJson<{ conversations: number }>('/api/conversations/unread-count')).conversations;
    const start = await unread();
    expect(start).toBeGreaterThan(0);
    await page.goto('/dashboard');
    // desktop: main navigation; phones: the bottom tab bar (both carry the badge)
    const badge = page.locator('[data-testid="nav.badge.messages"]:visible');
    await expect(badge).toHaveAttribute('data-count', String(start));
    await waitForRealtime(page);

    // a new message in a read conversation arrives over the WebSocket
    await sendMessage(apiOf(await as('bot')), CONVERSATIONS.foodsaverBot, 'Hast du am Freitag Zeit?');
    await expect(badge).toHaveAttribute('data-count', String(start + 1));
    await page.goto(`/messages/${CONVERSATIONS.foodsaverBot}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    await expect(badge).toHaveAttribute('data-count', String(start));
    await expectNoSeriousViolations(page);
  });

  test('N1 edit and delete own messages; the other member sees edited and deleted @N1', async ({ page, as }) => {
    await page.goto(`/messages/${CONVERSATIONS.foodsaverBot}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    const composer = page.getByTestId('messaging.conversation.composer');
    await composer.fill('Treffpunkt um 18 Uhr beim Bahnhof');
    await composer.press('Enter');
    await expect(page.getByTestId('messaging.conversation.pending')).toHaveCount(0);
    await expect(bubbles(page).last()).toContainText('18 Uhr');
    const seq = Number(await bubbles(page).last().getAttribute('data-seq'));
    const mine = bubble(page, seq);

    // edit in the composer: the draft is parked and comes back afterwards
    await composer.fill('Noch nicht fertig');
    await mine.hover();
    await mine.getByTestId('messaging.conversation.messageMenu').click();
    await page.getByTestId('messaging.conversation.edit').click();
    await expect(page.getByTestId('messaging.conversation.editBanner')).toBeVisible();
    await expect(composer).toHaveValue('Treffpunkt um 18 Uhr beim Bahnhof');
    // unchanged text: nothing to save
    await expect(page.getByTestId('messaging.conversation.editSave').locator('button')).toBeDisabled();
    await composer.fill('Treffpunkt um 19 Uhr beim Bahnhof');
    await composer.press('Enter');
    await expect(page.getByTestId('messaging.conversation.editBanner')).toHaveCount(0);
    await expect(mine.getByTestId('messaging.conversation.body')).toHaveText('Treffpunkt um 19 Uhr beim Bahnhof');
    await expect(mine).toHaveAttribute('data-edited', 'true');
    await expect(mine.getByTestId('messaging.conversation.edited')).toBeVisible();
    await expect(composer).toHaveValue('Noch nicht fertig');

    const botPage = await as('bot');
    await botPage.goto(`/messages/${CONVERSATIONS.foodsaverBot}`);
    await expectDataState(botPage, 'messaging.conversation.state', 'success');
    const theirs = bubble(botPage, seq);
    await expect(theirs.getByTestId('messaging.conversation.body')).toHaveText('Treffpunkt um 19 Uhr beim Bahnhof');
    await expect(theirs.getByTestId('messaging.conversation.edited')).toBeVisible();
    await expect(theirs.getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);

    // delete with confirmation: the row stays as a placeholder without actions
    await mine.hover();
    await mine.getByTestId('messaging.conversation.messageMenu').click();
    await expect(page.getByTestId('messaging.conversation.delete')).toHaveAttribute('data-moderation', 'false');
    await page.getByTestId('messaging.conversation.delete').click();
    await page.locator('.p-confirmdialog-accept-button').click();
    await expect(mine).toHaveAttribute('data-deleted', 'true');
    await expect(mine.getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'false');
    await expect(mine.getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);
    await expect(mine.getByTestId('messaging.conversation.body')).toHaveCount(0);

    await botPage.reload();
    await expectDataState(botPage, 'messaging.conversation.state', 'success');
    await expect(theirs.getByTestId('messaging.conversation.deleted')).toBeVisible();
    await botPage.goto('/messages');
    await expectDataState(botPage, 'messaging.list.state', 'success');
    await expect(listItem(botPage, CONVERSATIONS.foodsaverBot).getByTestId('messaging.list.preview')).toHaveAttribute('data-deleted', 'true');
    await expectNoSeriousViolations(page);
  });

  test('N1 error states: list 500 with retry, send failure keeps a pending bubble with retry @N1', async ({ page }) => {
    const injection = await injectApiError(page, { url: /\/api\/conversations(\?.*)?$/, method: 'GET', status: 500 });
    await page.goto('/messages');
    const { retry } = await expectErrorState(page, 'messaging.list.state');
    await injection.remove();
    await retry.click();
    await expectDataState(page, 'messaging.list.state', 'success');

    await listItem(page, CONVERSATIONS.foodsaverBot).click();
    await expectDataState(page, 'messaging.conversation.state', 'success');
    const failing = await injectApiError(page, { url: /\/messages$/, method: 'POST', status: 429, times: 1 });
    await page.getByTestId('messaging.conversation.composer').fill('Kommt das an?');
    await page.getByTestId('messaging.conversation.send').click();
    const pending = page.getByTestId('messaging.conversation.pending');
    await expect(pending).toHaveAttribute('data-failed', 'true');
    await failing.remove();
    await pending.getByTestId('messaging.conversation.retry').click();
    await expect(pending).toHaveCount(0);
    await expect(bubbles(page).last()).toContainText('Kommt das an?');

    await page.goto('/messages/00000000-0000-4000-8000-00000000dead');
    await expect(page.getByTestId('messaging.conversation.state')).toHaveAttribute('data-state', /notFound|forbidden/);
  });
});

test.describe('store manager', () => {
  test.use({ role: 'bieb' });

  test('N1 a store manager deletes a team member message with a reason; members see the moderation placeholder @N1 @S10', async ({ page, as }) => {
    await page.goto(`/messages/${CONVERSATIONS.s01Team}`);
    await expectDataState(page, 'messaging.conversation.state', 'success');
    // seeded: seq 5 deleted by the store manager
    await expect(bubble(page, 5).getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');

    // seq 2 "Danke für die Info!" by foodsaver: only the moderation delete, with a reason of at least 10 characters
    const target = bubble(page, 2);
    await target.hover();
    await target.getByTestId('messaging.conversation.messageMenu').click();
    await expect(page.getByTestId('messaging.conversation.edit')).toHaveCount(0);
    await expect(page.getByTestId('messaging.conversation.delete')).toHaveAttribute('data-moderation', 'true');
    await page.getByTestId('messaging.conversation.delete').click();
    const reason = page.getByTestId('messaging.conversation.deleteDialog.message');
    const confirm = page.getByTestId('messaging.conversation.deleteDialog.confirm');
    await expect(reason).toBeVisible();
    await reason.fill('zu kurz');
    await confirm.click();
    await expect(reason).toHaveAttribute('aria-invalid', 'true');
    await reason.fill('Gehört in den Direktchat, nicht ins Team.');
    await confirm.click();
    await expect(reason).toBeHidden();
    await expect(target.getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');

    const foodsaverPage = await as('foodsaver');
    await foodsaverPage.goto(`/messages/${CONVERSATIONS.s01Team}`);
    await expectDataState(foodsaverPage, 'messaging.conversation.state', 'success');
    await expect(bubble(foodsaverPage, 2).getByTestId('messaging.conversation.deleted')).toHaveAttribute('data-moderation', 'true');
    await expect(bubble(foodsaverPage, 2).getByTestId('messaging.conversation.messageMenu')).toHaveCount(0);
  });
});

test.describe('store chats', () => {
  test('S10 accepting a store application adds the user to the team chat; standby moves to jumper chat @S10 @page', async ({ as, apiOf, dev, store }) => {
    const s01 = store.id('S-01');
    const guide = await dev.user('guide');
    const guidePage = await as('guide');
    const guideApi = apiOf(guidePage);
    const biebApi = apiOf(await as('bieb'));
    const isMember = async (conversationId: string) =>
      (await biebApi.getJson<{ members: { id: string }[] }>(`/api/conversations/${conversationId}`)).members.some(
        (m) => m.id === guide.id,
      );
    expect(await isMember(CONVERSATIONS.s01Team)).toBe(false);

    await expectStatus(await guideApi.post(`/api/stores/${s01}/applications`, { text: 'Ich wohne gleich um die Ecke.' }), [200, 201, 204], 'apply');
    await expectStatus(await biebApi.post(`/api/stores/${s01}/applications/${guide.id}/accept`), [200, 201, 204], 'accept');
    await expect.poll(() => isMember(CONVERSATIONS.s01Team)).toBe(true);
    expect(await isMember(CONVERSATIONS.s01Jumper)).toBe(false);

    await guidePage.goto(`/stores/${s01}/chat`);
    await expectDataState(guidePage, 'stores.chat.state', 'success');
    await expect(guidePage.getByTestId('messaging.conversation.header')).toBeVisible();
    await expect(guidePage.getByTestId('messaging.conversation.rename')).toHaveCount(0);
    await guidePage.getByTestId('messaging.conversation.composer').fill('Hallo Team!');
    await guidePage.getByTestId('messaging.conversation.send').click();
    await expect(bubbles(guidePage).last()).toContainText('Hallo Team!');
    await expectNoSeriousViolations(guidePage);

    await expectStatus(
      await biebApi.post(`/api/stores/${s01}/team/${guide.id}/standby`, { message: 'Bis du wieder Zeit hast.' }),
      [200, 201, 204],
      'standby',
    );
    await expect.poll(() => isMember(CONVERSATIONS.s01Jumper)).toBe(true);
    expect(await isMember(CONVERSATIONS.s01Team)).toBe(false);
    await guidePage.reload();
    await expectDataState(guidePage, 'stores.chat.state', 'success');
    await expect(guidePage.getByTestId('messaging.conversation.header')).toBeVisible();
  });
});
