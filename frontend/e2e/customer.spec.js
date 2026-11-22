// A signed-in customer (USER role). The session comes from auth.setup.js.
import { expect, test } from './support/fixtures.js';
import { CUSTOMER } from './support/env.js';

test.use({ storageState: CUSTOMER.storageState });

test.describe('customer', () => {
  test('navigation shows Orders and Profile but not Admin', async ({ page }) => {
    await page.goto('/');
    const header = page.getByRole('banner');
    await expect(header.getByText(CUSTOMER.email)).toBeVisible();
    await expect(header.getByText('Orders', { exact: true })).toBeVisible();
    await expect(header.getByText('Profile', { exact: true })).toBeVisible();
    await expect(header.getByText('Admin', { exact: true })).toHaveCount(0);
  });

  test('profile shows own details and the password form', async ({ page }) => {
    await page.goto('/profile');
    await expect(page.locator('input[name=email]')).toHaveValue(CUSTOMER.email);
    await expect(page.getByText('Change password').first()).toBeVisible();
  });

  test('orders page lists own orders', async ({ page }) => {
    await page.goto('/orders');
    await expect(page.getByText('My orders & tracking')).toBeVisible();
  });

  test('admin area says "Admins only"', async ({ page }) => {
    await page.goto('/admin');
    await expect(page.getByText('Admins only')).toBeVisible();
  });

  test('logout clears the session', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('banner').getByRole('button', { name: 'Logout' }).click();
    await expect(page.getByRole('banner').getByText('Login', { exact: true })).toBeVisible();
    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login\?next=/);
  });
});
