// Runs before the specs (the "setup" project in playwright.config.js).
// Signs in through the real login form once per role and saves the browser session, so specs start already signed in.
import { expect, test as setup } from '@playwright/test';
import { ADMIN, CUSTOMER, GATEWAY_URL, PASSWORD } from './support/env.js';

setup('stack is reachable', async ({ page, request }) => {
  const health = await request.get(`${GATEWAY_URL}/actuator/health`).catch(() => null);
  expect(health?.ok(), `API gateway at ${GATEWAY_URL} is up (start the stack with: make up)`).toBeTruthy();
  await page.goto('/');
  await expect(page.getByText('Pet Planet').first(), 'storefront is up (start the stack with: make up)').toBeVisible();
});

for (const account of [CUSTOMER, ADMIN]) {
  setup(`sign in as ${account.email}`, async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[name=email]').fill(account.email);
    await page.locator('input[name=password]').fill(PASSWORD);
    await page.locator('form').getByRole('button', { name: 'Login' }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('banner').getByText(account.email)).toBeVisible();
    await page.context().storageState({ path: account.storageState });
  });
}
