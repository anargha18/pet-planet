// Small gateway helpers for test data. Tests create what they need through the API and check it through the UI.
import { expect, request } from '@playwright/test';
import { GATEWAY_URL, PASSWORD } from './env.js';

/** Returns an API client signed in as the given account (Authorization header pre-set). */
export async function apiAs(email) {
  const anonymous = await request.newContext({ baseURL: GATEWAY_URL });
  const login = await anonymous.post('/api/v1/auth/login', { data: { email, password: PASSWORD } });
  expect(login.ok(), `login as ${email} via the gateway`).toBeTruthy();
  const { token } = await login.json();
  await anonymous.dispose();
  return request.newContext({ baseURL: GATEWAY_URL, extraHTTPHeaders: { Authorization: `Bearer ${token}` } });
}

/**
 * Creates a uniquely named pet as admin and waits until inventory has registered it (it learns about new pets from a
 * Kafka event), so the pet can be ordered straight away.
 */
export async function createPet(adminApi, overrides = {}) {
  const response = await adminApi.post('/api/v1/pets', {
    data: {
      name: `E2E ${Date.now()}-${Math.floor(Math.random() * 1000)}`,
      species: 'CAT',
      breed: 'Playwright Shorthair',
      age: 2,
      gender: 'FEMALE',
      price: 77.5,
      description: 'Created by the Playwright end-to-end tests',
      ...overrides,
    },
  });
  expect(response.ok(), 'create pet as admin').toBeTruthy();
  const pet = await response.json();
  await expect
    .poll(async () => (await adminApi.get(`/api/v1/inventory/pets/${pet.id}`)).status(), {
      message: 'inventory registers the new pet',
      timeout: 20_000,
    })
    .toBe(200);
  return pet;
}
