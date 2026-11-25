# QA acceptance scenarios for EPMCDMETST-73807 - Prevent duplicate visit dates for the same pet.
# Each scenario is automated in:
#   - VisitDuplicateDateAcceptanceTests (Java, MockMvc against the full application on H2)
#   - e2e/tests/visit-duplicate-date.spec.ts (Playwright + TypeScript, real browser)
# Visit dates are always in the future because the existing rule requires "after today".
@EPMCDMETST-73807
Feature: Prevent duplicate visit dates for the same pet

  Background:
    Given an owner with a pet exists
    And I am on the "New Visit" page of that pet

  @AC1
  Scenario: Booking a visit on a unique future date succeeds
    Given the pet has no visit on a given future date
    When I submit the visit form with that date and a description
    Then I should be redirected to the owner details page
    And the visit is listed under the pet's visits
    And I should see the confirmation "Your visit has been booked"

  @AC2
  Scenario: Booking a second visit on the same date for the same pet is rejected
    Given the pet already has a visit on a given future date
    When I submit the visit form with that same date and a description
    Then I should stay on the "New Visit" form
    And I should see a field error on the date "This pet already has a visit booked for this date"
    And no additional visit is saved for the pet on that date

  @AC3
  Scenario: Different pets may have a visit on the same date
    Given pet A already has a visit on a given future date
    When I book a visit for pet B on that same date
    Then the visit for pet B is saved
    And I should be redirected to the owner details page
    And both pets have a visit on that date

  @AC4
  Scenario Outline: Today or a past date is still rejected with the existing error
    When I submit the visit form with a date that is <when>
    Then I should stay on the "New Visit" form
    And I should see the existing date error "Visit date must be in the future" on the date field
    And I should not see the duplicate-visit message
    And no visit is saved

    Examples:
      | when      |
      | today     |
      | yesterday |
      | in 2013   |

  @AC4 @precedence
  Scenario: The past-date rule takes precedence over the duplicate rule
    Given the pet already has a visit on a past date from existing data
    When I submit the visit form with that same past date
    Then only the existing "Visit date must be in the future" error is reported on the date field
