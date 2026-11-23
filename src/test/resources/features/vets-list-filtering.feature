# EPMCDMETST-73424 - Vets list filtering
# Seed data: Carter (none), Leary (radiology), Douglas (surgery, dentistry), Ortega (surgery),
# Stevens (radiology), Jenkins (none)
Feature: Filter the vets list by last name substring and specialty

  Scenario: Default / backward compatibility
    Given I navigate to the vets list page without any filter parameters
    When the page loads
    Then I see the complete list of vets

  Scenario: Filter by last name substring (case-insensitive)
    Given I am on the vets list page
    When I provide a last name filter value "OU" and apply the filter
    Then I see only vets whose last name contains "ou" ignoring case

  Scenario: Filter by specialty
    Given I am on the vets list page
    When I select the specialty "dentistry" and apply the filter
    Then I see only vets who have the specialty "dentistry"

  Scenario: Filter by both criteria
    Given I am on the vets list page
    When I provide last name "ou" and specialty "dentistry"
    Then I see only vets matching both criteria

  Scenario: Filters are combined with AND
    Given I am on the vets list page
    When I provide last name "carter" and specialty "dentistry"
    Then I see no vets

  Scenario: No matches
    Given I am on the vets list page
    When I apply filters that match no vets
    Then I see an empty results state indicating no vets match the filters

  Scenario: Clearing filters
    Given I previously applied filters on the vets list page
    When I follow the Clear link
    Then I see the complete list of vets again

  Scenario: Query parameter support
    Given I navigate to the vets list page using query parameters for last name and specialty
    When the page loads
    Then the list is filtered
    And the filter UI reflects the current filter values

  Scenario: Empty inputs treated as no filter
    Given I am on the vets list page
    When I submit the filter form with both last name and specialty empty
    Then I see the complete list of vets

  Scenario: JSON endpoint unchanged
    Given I request /vets as JSON with filter parameters
    Then all vets are returned
