import { acceptPendingConsents, expectDataState, regionId } from '../../fixtures';
import { checkpoint, expect, journey as test } from '../../fixtures/checkpoint';
import { Walk, isPhone } from '../../fixtures/persona';

/**
 * Ambassador Reto (bot@local) looks after Zürich Stadt: from "my regions" he opens the region, sets the video
 * conference link and an extra onboarding step in the settings (both in the region history), moves the region pin and
 * hides it from the public page. Sarah now sees the conference link on the overview. As admin of the PR group he
 * accepts Sarah's application, sharpens the teaser, declines Jonas with a message and writes to the group. Rules
 * behind it: RegionOptionsServiceTest (settings and pin need the admin/PR right), regions-pages.spec (https links
 * only, inactive pin), ConferenceLinkServiceTest (members only), GroupServiceTest (apply, accept, decline, mail).
 */
test.use({ role: 'bot' });

const ZURICH = regionId('zuerich-stadt');
const PR_GROUP = '00000011-0001-4000-8000-000000000028';
const CONFERENCE = 'https://meet.jit.si/foodsharing-zuerich';

test('ambassador sets up the region, moves its pin and runs his working group @R3 @R6 @R7 @R9 @R11 @R12 @D10.3', async ({
  page,
  as,
  apiOf,
  mails,
}, testInfo) => {
  test.setTimeout(180_000);
  await acceptPendingConsents(apiOf(page));
  await mails.clear();
  const walk = new Walk(page, testInfo, 'ambassador');
  const toast = page.getByTestId('shell.toast').locator('.p-toast-message').first();

  await walk.step('opens Zürich Stadt from his regions', async () => {
    await page.goto('/dashboard');
    await expect(page.getByTestId('dashboard.page.welcome')).toBeVisible();
    await walk.nav('regions');
    await expectDataState(page, 'regions.mine.state', 'success');
    await walk.click(page.getByTestId('regions.mine.region').filter({ hasText: 'Zürich Stadt' }));
    await expect(page).toHaveURL(new RegExp(`/regions/${ZURICH}/overview$`));
    await expect(page.getByTestId('regions.page.name')).toHaveText('Zürich Stadt');
    await expect(page.getByTestId('regions.overview.ambassadors')).toContainText('Reto Baumann');
    await checkpoint(page, 'region-overview');
  });

  await walk.step('sets the conference link and an extra onboarding step', async () => {
    await walk.click(page.getByTestId('regions.page.tab.settings'));
    await expectDataState(page, 'regions.settings.state', 'success');
    const url = page.getByTestId('regions.settings.conferenceUrl');
    const save = page.getByTestId('regions.settings.save');
    // only https links
    await walk.fill(url, 'ftp://meet.example.org');
    await expect(save.locator('button')).toBeDisabled();
    await walk.fill(url, CONFERENCE);
    const steps = page.getByTestId('regions.settings.step');
    const count = await steps.count();
    await walk.click(page.getByTestId('regions.settings.addStep'));
    await expect(steps).toHaveCount(count + 1);
    await walk.fill(page.getByTestId(`regions.settings.stepKey.${count}`), 'GARDEN_TOUR');
    await walk.fill(page.getByTestId(`regions.settings.stepTitles.${count}.field.de`), 'Gartenrundgang');
    await checkpoint(page, 'region-settings');
    await walk.click(save);
    await expect(toast).toBeVisible();
  });

  await walk.step('reads the change in the region history', async () => {
    await walk.click(page.getByTestId('regions.page.tab.history'));
    await expectDataState(page, 'regions.history.list', 'success');
    await expect(page.getByTestId('regions.history.entry').first()).toBeVisible();
    await checkpoint(page, 'region-history');
  });

  await walk.step('moves the region pin and hides it', async () => {
    await walk.click(page.getByTestId('regions.page.tab.pin'));
    await expectDataState(page, 'regions.pin.state', 'success');
    const coordinates = page.getByTestId('regions.pin.coordinates');
    const before = await coordinates.textContent();
    const map = page.getByTestId('regions.pin.map');
    const box = (await map.boundingBox())!;
    const at = { x: Math.round(box.width * 0.25), y: Math.round(box.height * 0.25) };
    // touch devices tap the map, desktops click it
    if (testInfo.project.use.hasTouch) await map.tap({ position: at });
    else await map.click({ position: at });
    walk.count(1);
    await expect(coordinates).not.toHaveText(before ?? '');
    await walk.click(page.getByTestId('regions.pin.active'));
    await checkpoint(page, 'region-pin');
    await walk.click(page.getByTestId('regions.pin.save'));
    await expect(toast).toBeVisible();
  });

  // an inactive pin is not on the public page
  await page.goto('/r/zuerich-stadt');
  await expect(page.getByTestId('regions.public.name')).toHaveText('Zürich Stadt');
  await expect(page.getByTestId('regions.public.map')).toHaveCount(0);

  // Sarah, a member, sees the conference link on the overview
  const sarah = await as('foodsaver');
  await sarah.goto(`/regions/${ZURICH}/overview`);
  const link = sarah.getByTestId('regions.overview.conference');
  await expect(link).toHaveAttribute('href', CONFERENCE);
  await expect(link).toHaveAttribute('target', '_blank');
  await expect(link).toHaveAttribute('rel', /noopener/);

  // Jonas applies to the PR group (seed: his earlier application was declined)
  const applied = await apiOf(await as('jumper')).post(`/api/groups/${PR_GROUP}/applications`, { text: 'Ich helfe gern an Ständen mit.' });
  expect(applied.status(), await applied.text()).toBe(201);

  const application = (name: string) => page.getByTestId('regions.group.application').filter({ hasText: name });
  await walk.step('accepts Sarah into the PR group and sharpens its teaser', async () => {
    await page.goto(`/regions/${ZURICH}`);
    await walk.click(page.getByTestId('regions.page.tab.groups'));
    await expectDataState(page, 'regions.groups.list', 'success');
    await walk.click(page.getByTestId('regions.groups.link.oeffentlichkeitsarbeit-zuerich'));
    await expect(page).toHaveURL(new RegExp(`/groups/${PR_GROUP}$`));
    await expectDataState(page, 'regions.group.state', 'success');
    await expect(application('Sarah Müller')).toBeVisible();
    await expect(application('Jonas Weber')).toBeVisible();
    await checkpoint(page, 'group-applications');
    await walk.click(application('Sarah Müller').getByTestId('regions.group.accept'));
    await expect(application('Sarah Müller')).toHaveCount(0);
    await expect(page.getByTestId('regions.group.members')).toContainText('Sarah Müller');

    await walk.click(page.getByTestId('regions.group.edit'));
    await walk.fill(page.getByTestId('regions.groupForm.teaser'), 'Wir erzählen von foodsharing.');
    await walk.click(page.getByTestId('regions.groupForm.submit'));
    await expect(page.getByTestId('regions.group.head')).toContainText('Wir erzählen von foodsharing.');
  });

  await walk.step('declines Jonas with a message', async () => {
    await walk.click(application('Jonas Weber').getByTestId('regions.group.decline'));
    await walk.fill(page.getByTestId('regions.group.declineDialog.message'), 'Das Team ist im Moment voll.');
    await checkpoint(page, 'decline-dialog');
    await walk.click(page.getByTestId('regions.group.declineDialog.confirm'));
    await expect(application('Jonas Weber')).toHaveCount(0);
  });

  await walk.step('writes to the group', async () => {
    const subject = 'Treffen am Montag';
    await walk.click(page.getByTestId('regions.group.mail'));
    await walk.fill(page.getByTestId('regions.group.mailSubject'), subject);
    await walk.fill(page.getByTestId('regions.group.mailBody'), 'Hallo zusammen, wir treffen uns am Montag.');
    await checkpoint(page, 'group-mail');
    await walk.click(page.getByTestId('regions.group.mailSend'));
    await expect(page.getByTestId('regions.group.mailDialog')).toBeHidden();
    await mails.waitForMail({ to: 'foodsaver@local', subjectContains: subject });
  });

  // the phone opens "Regionen" from the menu drawer (one tap more)
  walk.finish({ steps: 7, clicks: isPhone(page) ? 20 : 19, fields: 8 });
});
