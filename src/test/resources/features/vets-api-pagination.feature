# Documentation-only Gherkin (the repository has no Cucumber runner).
# Executable counterparts: VetControllerPaginationTests (mocked repository, 25 vets)
# and VetPaginationIntegrationTests (seeded H2 database, 6 vets).
# Source: Jira EPMCDMETST-73705 acceptance criteria and the approved implementation plan.
Feature: Optional pagination on the GET /vets JSON endpoint
  As an API consumer
  I want to optionally page through the vets list
  So that I can retrieve vets in bounded chunks without breaking existing clients

  Background:
    Given the clinic has 25 vets

  @AC1
  Scenario: Paginated response when page is provided
    When I send GET "/vets?page=0&size=10"
    Then the response status is 200
    And "items" has at most 10 vets
    And "metadata" contains page 0, size 10, totalItems 25 and totalPages 3

  @AC1
  Scenario: Page without size uses the default size of 10
    When I send GET "/vets?page=0"
    Then the response status is 200
    And "items" has 10 vets
    And "metadata" contains size 10

  @AC1
  Scenario: Last partial page contains the remaining vets
    When I send GET "/vets?page=2&size=10"
    Then the response status is 200
    And "items" has 5 vets

  @AC1
  Scenario: Maximum allowed size of 100 is accepted
    When I send GET "/vets?page=0&size=100"
    Then the response status is 200
    And "items" has 25 vets
    And "metadata" contains page 0, size 100, totalItems 25 and totalPages 1

  @AC2
  Scenario: No pagination parameters returns the legacy full list
    When I send GET "/vets"
    Then the response status is 200
    And the body has "vetList" containing all 25 vets
    And the body has no "items" and no "metadata"

  @AC2
  Scenario Outline: A size without page is ignored
    When I send GET "/vets?size=<size>"
    Then the response status is 200
    And the body has "vetList" containing all 25 vets
    And the body has no "metadata"

    Examples:
      | size |
      | 5    |
      | 0    |
      | 101  |
      | abc  |

  @AC3
  Scenario: Negative page is rejected
    When I send GET "/vets?page=-1&size=10"
    Then the response status is 400
    And the error is "Invalid 'page' parameter: must be a non-negative integer"

  @AC3
  Scenario: Zero size is rejected
    When I send GET "/vets?page=0&size=0"
    Then the response status is 400
    And the error is "Invalid 'size' parameter: must be an integer between 1 and 100"

  @AC3
  Scenario: Non-numeric page is rejected
    When I send GET "/vets?page=abc&size=10"
    Then the response status is 400
    And the error is "Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values"

  @AC3
  Scenario Outline: Size above the maximum or below 1 is rejected
    When I send GET "/vets?page=0&size=<size>"
    Then the response status is 400
    And the error is "Invalid 'size' parameter: must be an integer between 1 and 100"

    Examples:
      | size |
      | -5   |
      | 101  |
      | 1000 |

  @AC3
  Scenario Outline: Other non-numeric page values are rejected
    When I send GET "/vets" with page "<page>"
    Then the response status is 400
    And the error is "Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values"

    Examples:
      | page        |
      | 1.5         |
      |             |
      | 99999999999 |
      | 0x1         |

  @AC3
  Scenario: Non-numeric size is rejected
    When I send GET "/vets?page=0&size=abc"
    Then the response status is 400
    And the error is "Invalid pagination parameters: 'page' and 'size' must be numeric (integer) values"

  @AC4
  Scenario: Page beyond the available results returns an empty list
    When I send GET "/vets?page=9999&size=10"
    Then the response status is 200
    And "items" is an empty list
    And "metadata" contains page 9999, size 10, totalItems 25 and totalPages 3
