// What happens when the stored token is rejected by the API or has already expired.
import { expect, test } from './support/fixtures.js';
import { AUTH_STORAGE_KEY, CUSTOMER } from './support/env.js';

test.use({ storageState: CUSTOMER.storageState });

test.describe('session', () => {
  test('a token the API rejects (401) sends the user to /login?expired=1', async ({ page }) => {
    await page.goto('/orders');
    await expect(page.getByText('My orders & tracking')).toBeVisible();

    // Keep the token's shape and expiry, but break its signature, so only the backend can tell it is invalid.
    await page.evaluate((key) => {
      const auth = JSON.parse(localStorage.getItem(key));
      const [header, payload, signature] = auth.token.split('.');
      auth.token = `${header}.${payload}.${signature.slice(0, -4)}AAAA`;
      localStorage.setItem(key, JSON.stringify(auth));
    }, AUTH_STORAGE_KEY);
    await page.reload();

    await expect(page).toHaveURL(/\/login\?expired=1/);
    await expect(page.getByText('Your session has expired')).toBeVisible();
    expect(await page.evaluate((key) => localStorage.getItem(key), AUTH_STORAGE_KEY)).toBeNull();
  });

  test('a token past its expiry time is dropped when the app loads', async ({ page }) => {
    await page.goto('/');
    await page.evaluate((key) => {
      const b64 = (o) => btoa(JSON.stringify(o)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
      const token = `${b64({ alg: 'RS256' })}.${b64({ sub: '2', exp: Math.floor(Date.now() / 1000) - 60 })}.x`;
      localStorage.setItem(key, JSON.stringify({ token, id: 2, email: 'x', role: 'USER' }));
    }, AUTH_STORAGE_KEY);

    await page.goto('/orders');
    await expect(page).toHaveURL(/\/login/);
  });
});
