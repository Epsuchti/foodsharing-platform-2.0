import { expect, expectDataState, test } from '../../fixtures';

/**
 * R9–R12 working groups (M2 T18). Seed: Zürich Stadt groups `…0011…20`–`35`; kochgruppe (33) is OPEN;
 * STORES_COORDINATION (23) needs 10 pickups / 4 weeks; PR group (28): bot is admin, foodsaver applied (PENDING).
 */
const ZURICH = '00000010-0001-4000-8000-000000000040';
const group = (nn: string) => `00000011-0001-4000-8000-0000000000${nn}`;

test.use({ seedReset: 'test' });

test('R9 group list filters by category and joins an open group @R9', async ({ as }) => {
  const page = await as('guide');
  await page.goto(`/regions/${ZURICH}/groups`);
  await expectDataState(page, 'regions.groups.list', 'success');
  await page.getByTestId('regions.groups.join.kochgruppe-zuerich').click();
  await expect(page.locator('[data-group="kochgruppe-zuerich"]')).toContainText(/Mitglied|Membre|Member/);

  await page.goto(`/groups/${group('33')}`);
  await expect(page.getByTestId('regions.group.memberTag')).toBeVisible();
  await expect(page.getByTestId('regions.group.members')).toBeVisible();
});

test('R9 the requirements checklist shows what is missing; applying is not offered then @R9', async ({ as }) => {
  const page = await as('jumper');
  await page.goto(`/groups/${group('23')}`);
  await expectDataState(page, 'regions.group.state', 'success');
  await expect(page.locator('[data-requirement="minPickups"]')).toHaveClass(/missing/);
  await expect(page.locator('[data-requirement="minWeeks"]')).toHaveClass(/ok/);
  await expect(page.getByTestId('regions.group.apply')).toHaveCount(0);
  // non-members don't see the member list
  await expect(page.getByTestId('regions.group.members')).toHaveCount(0);
});

test('R9 R11 the group admin accepts an application, edits the group and sends a group mail @R9 @R11', async ({ as, mails }) => {
  const page = await as('bot');
  await page.goto(`/groups/${group('28')}`);
  await expectDataState(page, 'regions.group.state', 'success');
  const application = page.getByTestId('regions.group.application').filter({ hasText: 'Sarah Müller' });
  await application.getByTestId('regions.group.accept').click();
  await expect(application).toHaveCount(0);
  await expect(page.getByTestId('regions.group.members')).toContainText('Sarah Müller');

  await page.getByTestId('regions.group.edit').click();
  await page.getByTestId('regions.groupForm.teaser').fill('Wir erzählen von foodsharing.');
  await page.getByTestId('regions.groupForm.submit').click();
  await expect(page.getByTestId('regions.group.head')).toContainText('Wir erzählen von foodsharing.');

  // a unique subject instead of "sent after the machine's wall clock": captured mails carry the fixed server clock
  // (SEED_NOW), so a wall-clock bound fails as soon as the run's calendar day passes SEED_NOW
  const subject = `Treffen am Montag ${Date.now().toString(36)}`;
  await page.getByTestId('regions.group.mail').click();
  await page.getByTestId('regions.group.mailSubject').fill(subject);
  await page.getByTestId('regions.group.mailBody').fill('Hallo zusammen, wir treffen uns am Montag.');
  await page.getByTestId('regions.group.mailSend').click();
  await expect(page.getByTestId('regions.group.mailDialog')).toBeHidden();
  await mails.waitForMail({ to: 'foodsaver@local', subjectContains: subject });
});

test('R9 applying, withdrawing and declining with a message @R9', async ({ as }) => {
  // jumper applies again (the seed has a declined one) so there is something to decline
  const jumper = await as('jumper');
  await jumper.goto(`/groups/${group('28')}`);
  const apply = jumper.getByTestId('regions.group.apply');
  await apply.click();
  await jumper.getByTestId('regions.apply.message').fill('Zweiter Versuch');
  await jumper.getByTestId('regions.apply.confirm').click();
  await expect(jumper.getByTestId('regions.group.appliedTag')).toBeVisible();
  await jumper.getByTestId('regions.group.cancelApplication').click();
  await expect(jumper.getByTestId('regions.group.appliedTag')).toHaveCount(0);
  await apply.click();
  await jumper.getByTestId('regions.apply.message').fill('Dritter Versuch');
  await jumper.getByTestId('regions.apply.confirm').click();
  await expect(jumper.getByTestId('regions.group.appliedTag')).toBeVisible();

  const page = await as('bot');
  await page.goto(`/groups/${group('28')}`);
  const application = page.getByTestId('regions.group.application').filter({ hasText: 'Jonas Weber' });
  await application.getByTestId('regions.group.decline').click();
  const confirm = page.getByTestId('regions.group.declineDialog.confirm');
  await page.getByTestId('regions.group.declineDialog.message').fill('Das Team ist im Moment voll.');
  await confirm.click();
  await expect(application).toHaveCount(0);
});
