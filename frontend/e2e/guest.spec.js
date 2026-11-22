// A visitor who is not signed in: public pages, route guards and the login form.
import { expect, test } from './support/fixtures.js';
import { CUSTOMER, PASSWORD } from './support/env.js';

test.describe('guest', () => {
  test('home page and catalog are public', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('banner').getByText('Pet Planet')).toBeVisible();

    await page.getByRole('banner').getByText('Browse Pets', { exact: true }).click();
    await expect(page).toHaveURL('/pets');
    await expect(page.getByText('Meet your next companion')).toBeVisible();
    await expect(page.getByText('View details').first()).toBeVisible();
  });

  test('navigation hides signed-in entries', async ({ page }) => {
    await page.goto('/');
    const header = page.getByRole('banner');
    await expect(header.getByText('Login', { exact: true })).toBeVisible();
    for (const label of ['Orders', 'Profile', 'Admin']) {
      await expect(header.getByText(label, { exact: true })).toHaveCount(0);
    }
  });

  test('protected page redirects to login and returns there after signing in', async ({ page }) => {
    await page.goto('/orders');
    await expect(page).toHaveURL('/login?next=%2Forders');
    await expect(page.getByText('Please sign in to continue')).toBeVisible();

    await page.locator('input[name=email]').fill(CUSTOMER.email);
    await page.locator('input[name=password]').fill(PASSWORD);
    await page.locator('form').getByRole('button', { name: 'Login' }).click();

    await expect(page).toHaveURL('/orders');
    await expect(page.getByText('My orders & tracking')).toBeVisible();
  });

  test('admin area redirects to login', async ({ page }) => {
    await page.goto('/admin/orders');
    await expect(page).toHaveURL('/login?next=%2Fadmin%2Forders');
  });

  test('wrong password shows an error and stays on the login page', async ({ page }) => {
    await page.goto('/login');
    await page.locator('input[name=email]').fill(CUSTOMER.email);
    await page.locator('input[name=password]').fill('not-the-password');
    await page.locator('form').getByRole('button', { name: 'Login' }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL('/login');
  });

  test('unknown path shows "Page not found"', async ({ page }) => {
    await page.goto('/no-such-page');
    await expect(page.getByText('Page not found')).toBeVisible();
    await page.getByRole('link', { name: 'Back to home' }).click();
    await expect(page).toHaveURL('/');
  });
});
