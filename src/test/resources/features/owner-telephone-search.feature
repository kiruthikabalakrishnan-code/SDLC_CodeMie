# QA acceptance scenarios for EPMCDMETST-73769 - Search owners by telephone number.
# Each scenario is automated in:
#   - OwnerTelephoneSearchAcceptanceTests (Java, MockMvc against the full application on H2)
#   - e2e/tests/owner-telephone-search.spec.ts (Playwright + TypeScript, real browser)
# Telephone numbers are 10 digits because Owner.telephone is validated as 10 digits.
@EPMCDMETST-73769
Feature: Find Owners by telephone number

  Background:
    Given I am on the "Find Owners" page

  @AC1
  Scenario: Search owners by telephone returns matching owners
    Given an owner exists with telephone number "5551234000"
    When I search for owners using telephone "5551234000"
    Then I should be taken to the details of that owner

  @AC2
  Scenario: Telephone search supports partial match (starts-with)
    Given owners exist with telephone numbers "5551234000" and "5559999000"
    When I search for owners using telephone starting with "555"
    Then I should see a results list including both owners
    And no owner whose telephone does not start with "555" is listed

  @AC3
  Scenario: Telephone search with no matches shows no-results outcome
    Given no owner exists with telephone number "0000000"
    When I search for owners using telephone "0000000"
    Then I should stay on the "Find Owners" page
    And I should see the same "has not been found" outcome used for last-name searches

  @AC4
  Scenario: Last-name search remains available and unchanged
    Given an owner exists with last name "Smith"
    When I search for owners using last name starting with "Smi"
    Then I should be taken to the details of "Smith"

  @AC4
  Scenario: Last-name pagination links carry no telephone criteria
    When I search for owners with an empty last name
    Then I should see a paged list of all owners
    And the pagination links contain only the page parameter

  @AC5
  Scenario Outline: Empty or invalid telephone input is rejected with a validation message
    Given the telephone search type is selected
    When I search for owners using telephone "<input>"
    Then I should stay on the "Find Owners" page
    And I should see the validation message "<message>"
    And no owners are listed

    Examples:
      | input   | message             |
      |         | is required         |
      | abc     | must be all numeric |
      | 555-123 | must be all numeric |
      | 55 5    | must be all numeric |

  @Pagination
  Scenario: Telephone search results are paged and keep the search criteria
    Given more than 5 owners have a telephone starting with "6085"
    When I search for owners using telephone starting with "6085"
    Then I should see the first 5 owners and pagination links
    And every pagination link contains "searchType=telephone" and "telephone=6085"
    And requesting a page beyond the last page redirects to "/owners?page=1&searchType=telephone&telephone=6085"
