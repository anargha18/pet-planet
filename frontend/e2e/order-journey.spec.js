// The whole checkout saga through the UI, with two people at once:
// the customer buys a new pet and watches the order confirm; the admin marks the delivery as delivered;
// the customer then sees the delivery on the order page.
import { expect, test } from './support/fixtures.js';
import { ADMIN, CUSTOMER } from './support/env.js';
import { apiAs, createPet } from './support/api.js';

test.use({ storageState: CUSTOMER.storageState });

test('customer buys a pet, admin delivers it, customer sees it delivered', async ({ page, browser }) => {
  const adminApi = await apiAs(ADMIN.email);
  const pet = await createPet(adminApi);
  await adminApi.dispose();

  await test.step('customer adds the pet to the cart', async () => {
    await page.goto(`/pets/${pet.id}`);
    await page.getByRole('button', { name: 'Add to Cart' }).click();
    await expect(page).toHaveURL('/cart');
    await expect(page.getByText(pet.name)).toBeVisible();
  });

  let orderId;
  await test.step('customer checks out', async () => {
    await page.goto('/checkout');
    await expect(page.getByText('simulated').first()).toBeVisible();
    await page.getByRole('button', { name: 'Submit order' }).click();
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    orderId = new URL(page.url()).pathname.split('/').pop();
    await expect(page.getByText('Your order was placed')).toBeVisible();
  });

  await test.step('order page updates live until confirmed and shipped', async () => {
    await expect(page.getByText('Confirmed').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('Shipped with tracking number').first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText('is confirmed').first()).toBeVisible();
  });

  await test.step('admin marks the delivery as delivered', async () => {
    const adminContext = await browser.newContext({ storageState: ADMIN.storageState, viewport: { width: 1400, height: 1000 } });
    const adminPage = await adminContext.newPage();
    await adminPage.goto('/admin/deliveries');
    const row = adminPage.getByRole('row').filter({ has: adminPage.getByRole('link', { name: `#${orderId}`, exact: true }) });
    await expect(row).toBeVisible({ timeout: 20_000 });
    await row.getByRole('button', { name: 'Delivered' }).click();
    await expect(adminPage.getByText(`Order #${orderId}: Delivered`)).toBeVisible();
    await adminContext.close();
  });

  await test.step('customer sees the delivery after refreshing', async () => {
    // The tracking view is updated asynchronously from the delivery event; refresh like a user would.
    await expect(async () => {
      await page.reload();
      await expect(page.getByText('has been delivered').first()).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: 30_000 });
  });
});
