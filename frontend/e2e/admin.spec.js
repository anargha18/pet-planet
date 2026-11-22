// A signed-in administrator. The session comes from auth.setup.js.
import { expect, test } from './support/fixtures.js';
import { ADMIN, CUSTOMER } from './support/env.js';

test.use({ storageState: ADMIN.storageState });

test.describe('admin', () => {
  test('navigation shows Admin', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('banner').getByText('Admin', { exact: true })).toBeVisible();
  });

  // [path, text that proves the page rendered]
  const pages = [
    ['/admin', 'Deliveries'],
    ['/admin/pets', 'Availability is read-only'],
    ['/admin/inventory', 'Reservation expires'],
    ['/admin/orders', 'Every customer'],
    ['/admin/users', 'Deactivation is soft'],
    ['/admin/deliveries', 'Deliveries'],
  ];
  for (const [path, text] of pages) {
    test(`${path} renders`, async ({ page }) => {
      await page.goto(path);
      await expect(page.getByText(text).first()).toBeVisible();
    });
  }

  test('users page lists the seeded customer', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page.getByText(CUSTOMER.email).first()).toBeVisible();
  });
});
