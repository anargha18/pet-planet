# 13 · Playwright end-to-end tests

This chapter explains the browser tests in `frontend/e2e/`: what they are, how to run them, how to read a failure and how to add a test. It assumes you have never used Playwright before.

## What Playwright is

[Playwright](https://playwright.dev/) is a testing tool from Microsoft. It opens a real browser, clicks and types like a person would, and checks what appears on the screen. The tests are plain JavaScript files.

An **end-to-end (E2E) test** goes through the whole system. Here the path is browser → React storefront → API gateway → services → Kafka → back to the screen. A green run proves that a real user can do the things the tests describe. Unit tests prove that one class works on its own; E2E tests prove that all the parts work together.

Playwright gives you:

- **Auto-waiting.** `click()` waits until the button exists, is visible and is enabled, and `expect(...).toBeVisible()` retries until the condition holds or the timeout runs out. You almost never write `sleep`.
- **Isolation.** Every test gets a fresh browser context (its own cookies and `localStorage`), so tests cannot leak state into each other and can run in parallel.
- **Debugging tools.** On failure it keeps a screenshot, a video and a *trace*, a step-by-step recording you can replay. It also has an interactive UI mode.

## Before you start

| You need | Why | Check |
|---|---|---|
| The stack running | The tests drive the real app; they do not start it | `make up`, then open http://localhost:5173 |
| Node 20 | Runs Playwright | `node -v` |
| Playwright's Chromium (about 100 MB, downloaded once) | The browser the tests use | `make e2e` installs it; or `cd frontend && npm run e2e:install` |

The seeded accounts `customer@petplanet.com` and `admin@petplanet.com` (password `password123`) must exist. They come from the user-service Flyway migrations, so every fresh stack has them.

## Running the tests

### The one-line way

```sh
make up      # if the stack is not running yet
make e2e     # installs npm packages + Chromium if missing, then runs every test
```

The output lists each test with ✓ or ✘ and ends with, for example, `25 passed (21.0s)`. The exit code is 0 only when every test passed, so `make e2e` works in scripts and CI.

### All the scripts

Run these from the `frontend/` folder:

| Command | What it does | When to use it |
|---|---|---|
| `npm run e2e` | Runs every test headless (no visible window) | The normal run; what `make e2e` calls |
| `npm run e2e:headed` | Same, but you can watch the browser windows | To see what a test does |
| `npm run e2e:ui` | Opens Playwright's UI mode: pick tests, run them, step through each action with before/after snapshots, re-run on file save | Writing or debugging tests (**start here when learning**) |
| `npm run e2e:report` | Opens the HTML report of the last run in your browser | After a run, especially a failed one |
| `npm run e2e:install` | Downloads Playwright's Chromium | First time on a machine, or after upgrading `@playwright/test` |

Useful options; add them after `--`:

```sh
npm run e2e -- e2e/guest.spec.js              # one file
npm run e2e -- -g "logout"                    # tests whose title contains "logout"
npm run e2e -- --debug -g "logout"            # step through one test with the Playwright Inspector
npm run e2e -- --workers=1                    # one test at a time (slower, simpler logs)
npm run e2e -- --repeat-each=3                # run each test 3 times to catch flaky ones
npx playwright codegen http://localhost:5173  # click through the app and Playwright writes the code for you
```

### Different ports or hosts

| Variable | Default | Meaning |
|---|---|---|
| `APP_URL` | `http://localhost:5173` | Storefront the browser opens |
| `GATEWAY_URL` | `http://localhost:8089` | API gateway used for health checks and test data |
| `CI` | unset | When set: 1 worker, 1 retry, and `test.only` fails the run |

Example: `APP_URL=http://localhost:4173 npm run e2e`.

## What the tests cover

25 tests in 6 files. The `setup` project runs first, and the 5 spec files then run in parallel (2 workers).

| File | Signed in as | Tests |
|---|---|---|
| `auth.setup.js` | — | Gateway health and storefront are reachable (fails fast with "start the stack with: make up"); signs in through the login form as customer and as admin and saves each session to `e2e/.auth/` |
| `guest.spec.js` | nobody | Home page and catalog are public; navigation has no Orders/Profile/Admin; `/orders` redirects to `/login?next=%2Forders` and returns there after signing in; `/admin/...` redirects to login; wrong password shows an error; unknown path shows "Page not found" |
| `customer.spec.js` | customer | Navigation shows Orders and Profile but not Admin; profile shows own email and the password form; orders page renders; `/admin` says "Admins only"; logout clears the session |
| `session.spec.js` | customer | A token with a broken signature (the API answers 401) leads to `/login?expired=1`, shows "Your session has expired" and clears storage; a token past its `exp` is dropped on load |
| `admin.spec.js` | admin | Navigation shows Admin; each admin page (`/admin`, `pets`, `inventory`, `orders`, `users`, `deliveries`) renders; users page lists the seeded customer |
| `order-journey.spec.js` | customer + admin | The full saga in the UI: create a new pet (API, as admin) and wait until inventory knows it → customer adds it to the cart → checkout → order page updates live to Confirmed and "Shipped with tracking number" with a confirmation notification → the admin, in a second browser context, marks the delivery Delivered → the customer refreshes until "has been delivered" appears |

Every test also fails if the page logs a JavaScript error (the automatic fixture in `e2e/support/fixtures.js`). HTTP errors that the browser logs as "Failed to load resource" are ignored, because some tests trigger 401/403 on purpose.

### Test data

- Each `order-journey` run creates **one new pet and one order** named `E2E <timestamp>`. Nothing is deleted afterwards, so the catalog grows by one pet per run. `make reset` wipes all data if you want a clean slate.
- No other test changes data. The logout test only clears the browser's own storage.
- The tests do not depend on `make seed`, and they work on a fresh or a long-running stack.

## How the suite is organised

```
frontend/
├── playwright.config.js        Projects, timeouts, reporters, base URL
├── e2e/
│   ├── auth.setup.js           Runs first: stack check + sign-in per role
│   ├── guest.spec.js           ┐
│   ├── customer.spec.js        │
│   ├── session.spec.js         │ the tests
│   ├── admin.spec.js           │
│   ├── order-journey.spec.js   ┘
│   ├── support/
│   │   ├── env.js              URLs, accounts, storage keys
│   │   ├── fixtures.js         `test` + `expect` with the console-error check
│   │   └── api.js              Gateway helpers: apiAs(email), createPet(adminApi)
│   └── .auth/                  Saved sessions (generated, git-ignored)
└── e2e-results/                Report, screenshots, videos, traces (generated, git-ignored)
```

Key ideas in `playwright.config.js`:

- **Two projects.** `setup` runs `*.setup.js`, and `chromium` runs `*.spec.js` with `dependencies: ['setup']`. Signing in once and reusing the saved session (`storageState`) is faster than logging in inside every test, and it still goes through the real login form.
- **Choosing an account per file** is one line: `test.use({ storageState: CUSTOMER.storageState })`. A file without it runs as a guest.
- **Timeouts.** A test may take up to 90 s, because the checkout saga needs a few seconds. Each `expect` waits up to 15 s, and the order journey waits up to 30 s for "Confirmed".
- **Evidence on failure.** `trace`, `screenshot` and `video` are kept only for failed tests.
- **Viewport 1400×1000**, so the desktop navigation bar is visible (below 1200 px the app shows a menu drawer instead).

## When a test fails

1. Read the terminal output. It shows the failing line, what was expected, what Playwright actually found, and the paths of the screenshot, video and trace.
2. `npm run e2e:report` opens the HTML report. Click the failed test to see the error, the screenshot and the trace.
3. Open the trace (from the report, or `npx playwright show-trace <path>/trace.zip`). It shows every action with a DOM snapshot before and after, the network calls and the console. This usually answers "what was on the screen at that moment?"
4. Re-run just that test while watching: `npm run e2e:headed -- -g "<part of the title>"`, or use `npm run e2e:ui`.

Common causes:

| Symptom | Cause | Fix |
|---|---|---|
| `setup` fails with "API gateway … is up (start the stack with: make up)" | Stack not running or still starting | `make up` (it waits until all services are healthy) |
| `Executable doesn't exist at …ms-playwright…` | Chromium not downloaded | `npm run e2e:install` |
| Order journey times out waiting for "Confirmed" | Saga slow or stuck, often a service restarting or left with smoke-test settings | `make ps`; `make logs s=order-service`; run `make smoke` *or* `make e2e`, not both at once (smoke restarts services) |
| A text locator suddenly finds nothing | The UI wording changed | Update the text in the spec. The tests find elements by visible text and roles on purpose, so wording changes show up |
| Fails only in parallel | Two tests share data | Give each test its own data (as `createPet` does) |

## Writing a new test

1. Pick the file by who is signed in, or create `e2e/<area>.spec.js`.
2. Import from the fixtures, not from `@playwright/test`, so the console-error check applies:

   ```js
   import { expect, test } from './support/fixtures.js';
   import { CUSTOMER } from './support/env.js';

   test.use({ storageState: CUSTOMER.storageState });   // omit for a guest

   test('cart shows the pet I added', async ({ page }) => {
     await page.goto('/pets');
     await page.getByText('View details').first().click();
     await page.getByRole('button', { name: 'Add to Cart' }).click();
     await expect(page).toHaveURL('/cart');
   });
   ```

3. Find elements the way a user sees them. In order of preference: `getByRole('button', { name: 'Submit order' })`, `getByText('...')`, then `locator('input[name=email]')` for form fields (every input has a stable `name`).
4. Assert with `await expect(...)`, which retries. Never use a fixed `waitForTimeout`. For something that only appears after a refresh, wrap it in `expect(async () => { ... }).toPass()` (see `order-journey.spec.js`).
5. Create the data you need through the API (`e2e/support/api.js`) instead of relying on existing rows.
6. Run it in UI mode until it passes, then run the whole suite twice (`npm run e2e -- --repeat-each=2`) to check it is not flaky.

`npx playwright codegen http://localhost:5173` records your clicks as code. It is a good way to discover selectors; tidy the result before you commit it.

## Relation to the other end-to-end checks

| Check | Drives | Focus |
|---|---|---|
| `make smoke` | curl against the gateway | Saga, compensation, failure modes and security rules on the API (82 checks) |
| `make ui-check` | puppeteer-core with your locally installed Chrome | One long scripted walkthrough of every page (29 checks) |
| `make e2e` (this chapter) | Playwright with its own Chromium | Independent, parallel UI tests with reports, traces and UI mode |

Playwright and `make ui-check` overlap; whether to retire `ui-check` is an open item in [chapter 12](12-status-decisions-and-known-issues.md#known-issues-and-limitations). None of these checks runs in CI yet. A CI job would need the full stack (`make up`) and then `CI=1 make e2e`.
