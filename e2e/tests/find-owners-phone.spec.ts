import { expect, Page, test } from '@playwright/test';

// Sample data (src/main/resources/db/h2/data.sql):
//   George Franklin  6085551023 (only Franklin)
//   Betty  Davis     6085551749
//   Harold Davis     6085553198

async function search(page: Page, lastName: string, telephone: string) {
  await page.goto('/owners/find');
  await page.locator('#lastName').fill(lastName);
  await page.locator('#telephone').fill(telephone);
  await page.getByRole('button', { name: /find owner/i }).click();
}

test.describe('Find Owners by phone number', () => {
  test('find form shows both name and phone fields', async ({ page }) => {
    await page.goto('/owners/find');
    await expect(page.locator('#lastName')).toBeVisible();
    await expect(page.locator('#telephone')).toBeVisible();
  });

  test('phone only: digits-only input finds the owner', async ({ page }) => {
    await search(page, '', '6085551023');
    await expect(page).toHaveURL(/\/owners\/1$/);
    await expect(page.locator('body')).toContainText('George Franklin');
  });

  test('phone only: formatted input "(608) 555-1023" behaves like digits only', async ({ page }) => {
    await search(page, '', '(608) 555-1023');
    await expect(page).toHaveURL(/\/owners\/1$/);
    await expect(page.locator('body')).toContainText('George Franklin');
  });

  test('phone only: no match shows the standard not-found message', async ({ page }) => {
    await search(page, '', '0000000000');
    await expect(page).toHaveURL(/\/owners\?/);
    await expect(page.locator('#telephoneGroup .help-inline')).toContainText('has not been found');
  });

  test('name only: existing behaviour is unchanged', async ({ page }) => {
    await search(page, 'Davis', '');
    await expect(page).toHaveURL(/\/owners\?/);
    await expect(page.locator('table#owners tbody tr')).toHaveCount(2);
  });

  test('name only: no match shows the not-found message on the name field', async ({ page }) => {
    await search(page, 'Nobodyhere', '');
    await expect(page.locator('#lastNameGroup .help-inline')).toContainText('has not been found');
  });

  test('name and phone: both criteria are applied (AND)', async ({ page }) => {
    // "Davis" alone matches two owners; adding Betty's phone narrows to one.
    await search(page, 'Davis', '(608) 555-1749');
    await expect(page).toHaveURL(/\/owners\/\d+$/);
    await expect(page.locator('body')).toContainText('Betty Davis');
  });

  test('name and phone: matching name with another owner\'s phone returns nothing', async ({ page }) => {
    await search(page, 'Davis', '6085551023');
    await expect(page.locator('#lastNameGroup .help-inline')).toContainText('has not been found');
  });
});
