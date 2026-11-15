// Headless browser walkthrough of every customer and admin page against the RUNNING stack (`make up` first).
// Usage: make ui-check   (or: cd frontend && npm run ui-check [-- <screenshot dir>])
//
// Drives a locally installed Chrome/Chromium through puppeteer-core (no browser download). Set CHROME_PATH if Chrome is
// not at the default macOS location. It creates one pet (as admin) and one order (as the seeded customer) and marks
// that order delivered. Exit code 0 = every check passed.
import { existsSync, mkdirSync } from 'node:fs';
import puppeteer from 'puppeteer-core';

const APP = process.env.APP_URL || 'http://localhost:5173';
const API = process.env.GATEWAY || 'http://localhost:8089';
const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
  '/usr/bin/chromium-browser',
].filter(Boolean);
const chromePath = CHROME_CANDIDATES.find((path) => existsSync(path));
if (!chromePath) {
  console.error('No Chrome/Chromium found. Set CHROME_PATH to its executable.');
  process.exit(2);
}
const out = process.argv[2] || null; // screenshot directory (optional)
if (out) mkdirSync(out, { recursive: true });
let passed = 0;
let failed = 0;
const ok = (m) => { passed++; console.log(`  PASS ${m}`); };
const bad = (m) => { failed++; console.log(`  FAIL ${m}`); };
const check = (m, cond, detail = '') => (cond ? ok(m) : bad(`${m} ${detail}`));

const api = async (path, { token, method = 'GET', body } = {}) => {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  return res.status === 204 ? null : res.json();
};
const loginApi = async (email) => (await api('/api/v1/auth/login', { method: 'POST', body: { email, password: 'password123' } })).token;

const browser = await puppeteer.launch({
  executablePath: chromePath,
  headless: true,
  args: ['--no-first-run', '--no-default-browser-check', '--disable-features=PasswordLeakDetection,PasswordManagerOnboarding,PasswordCheck', '--disable-save-password-bubble'],
});
const consoleErrors = [];
const failedResponses = [];
let page;
/**
 * Opens a fresh tab (same browser profile, so the stored session carries over). Used after typing a password into the
 * login form: headless Chrome then shows a native password prompt that swallows later input events in that tab.
 */
const newTab = async () => {
  const previous = page;
  page = await browser.newPage();
  await page.setViewport({ width: 1400, height: 1000 });
  page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text().slice(0, 300)); });
  page.on('pageerror', (err) => consoleErrors.push(`pageerror: ${err.message}`));
  page.on('response', (r) => { if (r.status() >= 400) failedResponses.push(`${r.status()} ${r.url()}`); });
  if (previous) await previous.close();
};
await newTab();
const text = () => page.evaluate(() => document.body.innerText);
const waitText = async (t, timeout = 15000) => {
  try { await page.waitForFunction((s) => document.body.innerText.includes(s), { timeout }, t); return true; } catch { return false; }
};
// Settle after load: in dev mode React StrictMode runs effects twice, so data-loaded elements re-render once.
const go = async (path) => { await page.goto(APP + path, { waitUntil: 'networkidle2' }); await new Promise((r) => setTimeout(r, 700)); };
const shot = (name) => (out ? page.screenshot({ path: `${out}/${name}.png`, fullPage: true }) : Promise.resolve());
/** Real mouse click at the centre of the first button containing the label (after the page settles). */
const click = async (label) => {
  const handle = await page.waitForFunction((l) => [...document.querySelectorAll('button')].find((b) => b.innerText.includes(l) && !b.disabled), { timeout: 15000 }, label);
  await handle.evaluate((b) => b.scrollIntoView({ block: 'center' }));
  await new Promise((r) => setTimeout(r, 300));
  const box = await handle.asElement().boundingBox();
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
};
const loginUi = async (email, expectedPath) => {
  await page.locator('input[name=email]').fill(email);
  await page.locator('input[name=password]').fill('password123');
  await page.locator('button[type=submit]').click();
  await page.waitForFunction((p) => location.pathname === p, { timeout: 10000 }, expectedPath).catch(() => {});
  const landed = new URL(page.url()).pathname;
  await newTab();
  await go(landed);
  return landed;
};
const navLabels = () => page.$$eval('header a, header button', (els) => els.map((e) => e.innerText.trim()).filter(Boolean));

console.log('== Guest');
await go('/');
await page.evaluate(() => localStorage.clear());
await go('/orders');
check('guest /orders redirects to login with next', page.url().includes('/login?next=%2Forders'), page.url());
check('login asks to sign in', await waitText('Please sign in to continue'));
let nav = await navLabels();
check('guest nav has no Orders/Profile/Admin', !nav.some((l) => ['Orders', 'Profile', 'Admin'].includes(l)), JSON.stringify(nav));

console.log('== Customer');
const adminToken = await loginApi('admin@petplanet.com');
const pet = await api('/api/v1/pets', { token: adminToken, method: 'POST', body: {
  name: `UI ${Date.now()}`, species: 'CAT', breed: 'UI Breed', age: 2, gender: 'FEMALE', price: 77.5, description: 'UI walkthrough pet' } });
await new Promise((r) => setTimeout(r, 3000)); // inventory learns about the pet
check('after login returns to /orders', (await loginUi('customer@petplanet.com', '/orders')) === '/orders');
check('orders list renders', await waitText('My orders & tracking'));
nav = await navLabels();
check('customer nav has Orders + Profile, no Admin', nav.includes('Orders') && nav.includes('Profile') && !nav.includes('Admin'), JSON.stringify(nav));
await shot('01-orders');

await go('/admin');
check('customer on /admin sees "Admins only"', await waitText('Admins only'));

await go('/profile');
check('profile loads own details', await waitText('customer@petplanet.com') && await waitText('Change password'));
await shot('02-profile');

await go(`/pets/${pet.id}`);
await click('Add to Cart');
await page.waitForFunction(() => location.pathname === '/cart', { timeout: 10000 });
check('add to cart lands on the cart with the pet', await waitText(pet.name));
await go('/checkout');
check('checkout shows simulated payment note', await waitText('simulated'));
await shot('03-checkout');
await click('Submit order');
await page.waitForFunction(() => /^\/orders\/\d+$/.test(location.pathname), { timeout: 15000 }).catch(() => {});
const orderId = new URL(page.url()).pathname.split('/').pop();
check('submit navigates to the order page', /^\d+$/.test(orderId), page.url());
check('order page shows success banner', await waitText('Your order was placed'));
check('order page polls until Confirmed', await waitText('Confirmed', 30000));
check('timeline shows the shipment', await waitText('Shipment created', 30000));
check('notification shown', await waitText('is confirmed'));
await shot('04-order-detail');

console.log('== Expired / rejected session');
await page.evaluate(() => {
  const auth = JSON.parse(localStorage.getItem('pet-planet-auth'));
  const parts = auth.token.split('.');
  auth.token = `${parts[0]}.${parts[1]}.${parts[2].slice(0, -4)}AAAA`; // valid shape and exp, invalid signature
  localStorage.setItem('pet-planet-auth', JSON.stringify(auth));
  return true;
});
await go('/orders');
await page.waitForFunction(() => location.pathname === '/login', { timeout: 10000 }).catch(() => {});
check('401 from the API redirects to /login?expired=1', page.url().includes('expired=1'), page.url());
check('expired message shown', await waitText('Your session has expired'));
check('session cleared', await page.evaluate(() => localStorage.getItem('pet-planet-auth') === null));
await shot('05-expired');
const expiredToken = await page.evaluate(() => {
  const b64 = (o) => btoa(JSON.stringify(o)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'RS256' })}.${b64({ sub: '2', exp: Math.floor(Date.now() / 1000) - 60 })}.x`;
});
await page.evaluate((t) => localStorage.setItem('pet-planet-auth', JSON.stringify({ token: t, id: 2, email: 'x', role: 'USER' })), expiredToken);
await go('/orders');
check('token past its exp is dropped on load', page.url().includes('/login'), page.url());

console.log('== Admin');
await go('/login');
await loginUi('admin@petplanet.com', '/');
nav = await navLabels();
check('admin nav has Admin', nav.includes('Admin'), JSON.stringify(nav));
for (const [path, heading, rowText] of [
  ['/admin', 'Deliveries', 'Inventory'],
  ['/admin/pets', 'Availability is read-only', pet.name],
  ['/admin/inventory', 'Reservation expires', 'Sold'],
  ['/admin/orders', 'Every customer', `#${orderId}`],
  ['/admin/users', 'Deactivation is soft', 'customer@petplanet.com'],
]) {
  await go(path);
  check(`${path} renders`, await waitText(heading) && await waitText(rowText), `(${heading} / ${rowText})`);
  await shot(`06-admin-${path.split('/').pop() || 'home'}`);
}
await go('/admin/deliveries');
check('/admin/deliveries lists the new order', await waitText(`#${orderId}`, 20000));
const row = await page.$(`::-p-xpath(//tr[.//a[normalize-space(.)="#${orderId}"]])`);
const deliveredButton = row && await row.$('::-p-xpath(.//button[contains(., "Delivered")])');
if (deliveredButton) {
  await deliveredButton.click();
  check('advance delivery succeeds', await waitText(`Order #${orderId}: Delivered`));
} else {
  bad('Delivered button not found for the new order');
}
await shot('07-admin-deliveries');
// The tracking view updates asynchronously from the delivery event, and the order page only polls while the saga is
// running, so reload like a user pressing Refresh until the delivery shows up (or ~20 s pass).
let delivered = false;
for (let attempt = 0; attempt < 10 && !delivered; attempt++) {
  await go(`/orders/${orderId}`);
  delivered = await waitText('has been delivered', 2000);
}
check('order page shows delivered status + notification', delivered);
await shot('08-order-delivered');

const unexpected = consoleErrors.filter((e) => !e.startsWith('Failed to load resource'));
check('no JavaScript errors in the browser console', unexpected.length === 0, JSON.stringify(unexpected.slice(0, 5)));
console.log('  (HTTP >= 400 seen:', JSON.stringify([...new Set(failedResponses.map((r) => r.replace(/\/\d+$/, '/{id}')))]), ')');
await browser.close();
console.log(`== UI result: ${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
