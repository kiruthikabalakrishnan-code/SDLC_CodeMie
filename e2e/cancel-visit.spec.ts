import { expect, test, Page } from '@playwright/test';

// Owner 6 (Jean Coleman) owns pet 7 (Samantha). Each test books its own visit so it
// does not depend on, or permanently change, the seeded visits.
// Spring may append ;jsessionid=... to the redirect URL.
const OWNER_URL_PATTERN = /\/owners\/6(;jsessionid=[^/?]*)?$/;
const CONFIRM_TEXT ='Are you sure you want to cancel this visit?';

async function bookVisit(page: Page, description: string) {
  await page.goto('/owners/6/pets/7/visits/new');
  await page.locator('#description').fill(description);
  await page.getByRole('button', { name: 'Add Visit' }).click();
  await expect(page).toHaveURL(OWNER_URL_PATTERN);
  await expect(visitRow(page, description)).toHaveCount(1);
}

function visitRow(page: Page, description: string) {
  // the owner page nests the visits table inside a pet row, so exclude rows that contain other rows
  return page.locator('tr', { has: page.locator('td', { hasText: description }) })
    .filter({ hasNot: page.locator('tr') })
    .filter({ has: page.getByRole('button', { name: 'Cancel' }) });
}

function cancelButtons(page: Page) {
  return page.getByRole('button', { name: 'Cancel' });
}

test('AC1: accepting the confirm dialog cancels the visit and shows a success message', async ({ page }) => {
  const description = `e2e-ac1-${Date.now()}`;
  await bookVisit(page, description);
  const before = await cancelButtons(page).count();

  page.once('dialog', async (dialog) => {
    expect(dialog.type()).toBe('confirm');
    expect(dialog.message()).toBe(CONFIRM_TEXT);
    await dialog.accept();
  });
  await visitRow(page, description).getByRole('button', { name: 'Cancel' }).click();

  await expect(page).toHaveURL(OWNER_URL_PATTERN);
  await expect(page.locator('#success-message')).toContainText('Visit cancelled successfully.');
  await expect(visitRow(page, description)).toHaveCount(0);
  await expect(cancelButtons(page)).toHaveCount(before - 1);
});

test('AC2: dismissing the confirm dialog keeps the visit and sends no request', async ({ page }) => {
  const description = `e2e-ac2-${Date.now()}`;
  await bookVisit(page, description);
  const before = await cancelButtons(page).count();

  const deleteRequests: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && request.url().endsWith('/delete')) {
      deleteRequests.push(request.url());
    }
  });

  page.once('dialog', async (dialog) => {
    expect(dialog.message()).toBe(CONFIRM_TEXT);
    await dialog.dismiss();
  });
  await visitRow(page, description).getByRole('button', { name: 'Cancel' }).click();

  await expect(page).toHaveURL(OWNER_URL_PATTERN);
  await expect(visitRow(page, description)).toHaveCount(1);
  await expect(cancelButtons(page)).toHaveCount(before);
  await expect(page.getByText('Visit cancelled successfully.')).toHaveCount(0);
  expect(deleteRequests).toHaveLength(0);

  // clean up the visit created by this test
  page.once('dialog', (dialog) => dialog.accept());
  await visitRow(page, description).getByRole('button', { name: 'Cancel' }).click();
  await expect(visitRow(page, description)).toHaveCount(0);
});
