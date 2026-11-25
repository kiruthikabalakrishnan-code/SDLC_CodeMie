import { expect, Page, test } from '@playwright/test';

/**
 * UI tests for EPMCDMETST-73769 - search owners by telephone number.
 * Scenarios mirror src/test/resources/features/owner-telephone-search.feature.
 *
 * Owners created by a test use a telephone prefix that is unique to the test run, so the
 * tests also work against a long-running instance whose data persisted from earlier runs.
 */

// 5-digit run prefix (never 6085x, which is used by the sample data) plus a per-test digit
const runPrefix = `55${String(Math.floor(Math.random() * 900) + 100)}`;
let counter = 0;
const uniquePrefix = () => `${runPrefix}${counter++ % 10}`;

async function createOwner(page: Page, firstName: string, lastName: string, telephone: string) {
  await page.goto('/owners/new');
  await page.fill('#firstName', firstName);
  await page.fill('#lastName', lastName);
  await page.fill('#address', '1 Test Street');
  await page.fill('#city', 'Testville');
  await page.fill('#telephone', telephone);
  await page.getByRole('button', { name: /add owner/i }).click();
  await expect(page).toHaveURL(/\/owners\/\d+(;jsessionid=\w+)?$/);
  return Number(/\/owners\/(\d+)/.exec(page.url())![1]);
}

async function searchByTelephone(page: Page, telephone: string) {
  await page.goto('/owners/find');
  await page.check('#searchByTelephone');
  await page.fill('#telephone', telephone);
  await page.getByRole('button', { name: /find owner/i }).click();
}

async function searchByLastName(page: Page, lastName: string) {
  await page.goto('/owners/find');
  await page.check('#searchByLastName');
  await page.fill('#lastName', lastName);
  await page.getByRole('button', { name: /find owner/i }).click();
}

async function paginationHrefs(page: Page) {
  return page
    .locator('a[href*="/owners?page="]')
    .evaluateAll((els) => els.map((e) => e.getAttribute('href') ?? ''));
}

test.describe('Find Owners page', () => {
  test('offers Last name and Telephone search types, Last name selected by default', async ({ page }) => {
    await page.goto('/owners/find');
    await expect(page.locator('#searchByLastName')).toBeChecked();
    await expect(page.locator('#searchByTelephone')).not.toBeChecked();
    await expect(page.locator('#lastName')).toBeVisible();
    await expect(page.locator('#telephone')).toBeVisible();
  });
});

test.describe('AC1 - search owners by telephone returns matching owners', () => {
  test('full telephone of a single owner opens that owner details', async ({ page }) => {
    const telephone = `${uniquePrefix()}0001`;
    const id = await createOwner(page, 'Anna', 'Alphaqa', telephone);

    await searchByTelephone(page, telephone);

    await expect(page).toHaveURL(new RegExp(`/owners/${id}(;jsessionid=[A-Za-z0-9]+)?$`));
    await expect(page.locator('body')).toContainText('Anna Alphaqa');
    await expect(page.locator('body')).toContainText(telephone);
  });

  test('sample data owner is found by full telephone', async ({ page }) => {
    await searchByTelephone(page, '6085551023');
    await expect(page).toHaveURL(/\/owners\/1$/);
    await expect(page.locator('body')).toContainText('George Franklin');
  });
});

test.describe('AC2 - telephone search supports partial (starts-with) match', () => {
  test('prefix lists every owner whose telephone starts with it', async ({ page }) => {
    const prefix = uniquePrefix();
    await createOwner(page, 'Anna', 'Alphaqa', `${prefix}1234`);
    await createOwner(page, 'Bob', 'Betaqa', `${prefix}9999`);

    await searchByTelephone(page, prefix);

    await expect(page).toHaveURL(/\/owners\?.*telephone=/);
    const rows = page.locator('table#owners tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(page.locator('table#owners')).toContainText(`${prefix}1234`);
    await expect(page.locator('table#owners')).toContainText(`${prefix}9999`);
    await expect(page.locator('body')).not.toContainText('Franklin');
  });
});

test.describe('AC3 - telephone search with no matches', () => {
  test('stays on Find Owners with the same not-found message as last-name search', async ({ page }) => {
    await searchByTelephone(page, '0000000');
    await expect(page.locator('#search-owner-form')).toBeVisible();
    await expect(page.locator('#telephoneGroup')).toContainText('has not been found');
    await expect(page.locator('#searchByTelephone')).toBeChecked();
    await expect(page.locator('#telephone')).toHaveValue('0000000');

    await searchByLastName(page, 'Nonexistentname');
    await expect(page.locator('#lastNameGroup')).toContainText('has not been found');
  });
});

test.describe('AC4 - last-name search remains available and unchanged', () => {
  test('last name prefix with one match opens the owner', async ({ page }) => {
    await searchByLastName(page, 'Fran');
    await expect(page).toHaveURL(/\/owners\/1$/);
    await expect(page.locator('body')).toContainText('George Franklin');
  });

  test('last name prefix with several matches lists them', async ({ page }) => {
    await searchByLastName(page, 'Dav');
    await expect(page.locator('table#owners tbody tr')).toHaveCount(2);
    await expect(page.locator('body')).toContainText('Betty Davis');
    await expect(page.locator('body')).toContainText('Harold Davis');
  });

  test('empty last name lists owners and pagination links carry no telephone criteria', async ({ page }) => {
    await searchByLastName(page, '');
    await expect(page.locator('table#owners tbody tr')).toHaveCount(5);
    const hrefs = await paginationHrefs(page);
    expect(hrefs.length).toBeGreaterThan(0);
    for (const href of hrefs) {
      expect(href).not.toContain('searchType');
      expect(href).not.toContain('telephone');
    }
  });
});

test.describe('AC5 - empty and invalid telephone input', () => {
  test('empty telephone shows a required validation message and does not list owners', async ({ page }) => {
    await searchByTelephone(page, '');
    await expect(page.locator('#telephoneGroup')).toContainText('is required');
    await expect(page.locator('table#owners')).toHaveCount(0);
    await expect(page.locator('#searchByTelephone')).toBeChecked();
  });

  test('whitespace-only telephone shows a required validation message', async ({ page }) => {
    await searchByTelephone(page, '   ');
    await expect(page.locator('#telephoneGroup')).toContainText('is required');
  });

  for (const input of ['abc', '555-123', '55 5']) {
    test(`non numeric telephone "${input}" shows a numeric validation message`, async ({ page }) => {
      await searchByTelephone(page, input);
      await expect(page.locator('#telephoneGroup')).toContainText('must be all numeric');
      await expect(page.locator('table#owners')).toHaveCount(0);
    });
  }
});

test.describe('Pagination of telephone results', () => {
  test('links keep search criteria and lead to the second page', async ({ page }) => {
    await searchByTelephone(page, '6085');
    await expect(page.locator('table#owners tbody tr')).toHaveCount(5);

    const hrefs = await paginationHrefs(page);
    expect(hrefs.length).toBeGreaterThan(0);
    for (const href of hrefs) {
      expect(href).toContain('searchType=telephone');
      expect(href).toContain('telephone=6085');
    }

    await page.locator('a[href*="page=2"]').first().click();
    await expect(page).toHaveURL(/page=2.*searchType=telephone.*telephone=6085/);
    await expect(page.locator('table#owners tbody tr')).toHaveCount(5);
  });

  test('out of range page redirects to page 1 keeping the telephone criteria', async ({ page }) => {
    await page.goto('/owners?searchType=telephone&telephone=6085&page=99');
    await expect(page).toHaveURL(/\/owners\?page=1&searchType=telephone&telephone=6085$/);
    await expect(page.locator('table#owners tbody tr')).toHaveCount(5);
  });
});
