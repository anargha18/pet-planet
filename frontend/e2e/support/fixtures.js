// Every spec imports `test` and `expect` from here instead of '@playwright/test'.
// The extra automatic fixture fails a test if the page logs a JavaScript error.
import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  failOnConsoleErrors: [
    async ({ page }, use) => {
      const errors = [];
      page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
      page.on('console', (message) => {
        // HTTP 4xx/5xx show up as "Failed to load resource"; some tests expect those (401, 403), so they are ignored.
        if (message.type() === 'error' && !message.text().startsWith('Failed to load resource')) {
          errors.push(message.text());
        }
      });
      await use();
      expect(errors, 'no JavaScript errors in the browser console').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
