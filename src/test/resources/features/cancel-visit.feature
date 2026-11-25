@EPMCDMETST-73697
Feature: Cancel a booked visit from the owner details page
  As a clinic user
  I want to cancel a booked visit from the owner details page
  So that visits that will not take place are removed from the schedule

  Background:
    Given owner 6 "Jean Coleman" owns pet 7 "Samantha"
    And pet 7 has a booked visit "annual checkup"

  # ---- UI (Playwright, e2e/cancel-visit.spec.ts) ----

  @ui @AC1
  Scenario: Confirming the dialog cancels the visit
    Given I am on the owner details page of owner 6
    When I click "Cancel" on the visit "annual checkup" and accept the confirm dialog
    Then I am redirected to the owner details page of owner 6
    And I see the message "Visit cancelled successfully."
    And the visit "annual checkup" is no longer listed

  @ui @AC2
  Scenario: Dismissing the dialog keeps the visit
    Given I am on the owner details page of owner 6
    When I click "Cancel" on the visit "annual checkup" and dismiss the confirm dialog
    Then no request is sent to the server
    And the visit "annual checkup" is still listed

  # ---- Backend (VisitCancellationControllerTests, VisitCancellationIntegrationTests) ----

  @backend @success @AC1
  Scenario: Cancelling an existing visit deletes it and redirects with a success message
    When I POST to "/owners/6/pets/7/visits/{visitId}/delete"
    Then the response is a redirect to "/owners/6"
    And the flash message is "Visit cancelled successfully."
    And there is no flash error
    And the visit is removed from the pet

  @backend @orphan-removal @AC1
  Scenario: Cancelling a visit physically deletes its database row
    Given the visits table contains a row for the visit
    When I POST to "/owners/6/pets/7/visits/{visitId}/delete"
    Then the visits table no longer contains a row for the visit
    And the other visits of the pet are still present
    And the visits of other pets are unchanged

  @backend @failure @AC3
  Scenario: Cancelling a visit of an unknown owner deletes nothing
    When I POST to "/owners/9999/pets/7/visits/{visitId}/delete"
    Then the response is a redirect to "/owners/9999"
    And the flash error is "Owner not found with id: 9999."
    And no visit is deleted
    And the response status is not 500

  @backend @failure @AC3
  Scenario: Cancelling a visit of an unknown pet deletes nothing
    When I POST to "/owners/6/pets/9999/visits/{visitId}/delete"
    Then the response is a redirect to "/owners/6"
    And the flash error is "Pet with id 9999 not found for owner with id 6."
    And no visit is deleted
    And the response status is not 500

  @backend @failure @AC3
  Scenario: Cancelling an unknown visit deletes nothing
    When I POST to "/owners/6/pets/7/visits/9999/delete"
    Then the response is a redirect to "/owners/6"
    And the flash error is "Visit with id 9999 not found for pet with id 7."
    And no visit is deleted
    And the response status is not 500

  @backend @failure @mismatch @AC4
  Scenario: Cancelling a visit that belongs to another owner's pet deletes nothing
    Given pet 1 "Leo" of owner 1 has a booked visit that does not belong to owner 6
    When I POST to "/owners/6/pets/7/visits/{otherOwnerVisitId}/delete"
    Then the response is a redirect to "/owners/6"
    And the flash error is "Visit with id {otherOwnerVisitId} not found for pet with id 7."
    And the visit of pet 1 is still present
    And the response status is not 500

  @backend @failure @mismatch @AC4
  Scenario: Cancelling a visit through a pet that does not own it deletes nothing
    Given pet 8 "Max" of owner 6 has a visit that does not belong to pet 7
    When I POST to "/owners/6/pets/7/visits/{otherPetVisitId}/delete"
    Then the response is a redirect to "/owners/6"
    And the flash error is "Visit with id {otherPetVisitId} not found for pet with id 7."
    And the visit of pet 8 is still present

  @backend @failure @mismatch @AC4
  Scenario: Cancelling a visit through a pet that belongs to another owner deletes nothing
    Given pet 1 "Leo" belongs to owner 1 and not to owner 6
    When I POST to "/owners/6/pets/1/visits/{visitId}/delete"
    Then the response is a redirect to "/owners/6"
    And the flash error is "Pet with id 1 not found for owner with id 6."
    And no visit is deleted

  @backend @failure @mismatch @AC4
  Scenario: Cancelling a visit through an owner that does not own the pet deletes nothing
    When I POST to "/owners/1/pets/7/visits/{visitId}/delete"
    Then the response is a redirect to "/owners/1"
    And the flash error is "Pet with id 7 not found for owner with id 1."
    And no visit is deleted

  @backend @method
  Scenario: Cancelling a visit is only possible with POST
    When I GET "/owners/6/pets/7/visits/{visitId}/delete"
    Then the response status is 405
    And no visit is deleted
