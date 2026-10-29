Feature: UC4 - Recipient pickup
  As a recipient
  I want to open the compartment with my pickup code
  So that I can collect my parcel

  Background:
    Given the system is clean

  Scenario: Pickup with a valid code frees the compartment
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-PICK" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I pick up with code "abcd2345"
    Then the response status is 200
    And the response field "currentState" is "PICKED_UP"
    And the response does not contain field "pickupCodeHash"
    And the parcel "TRK-PICK" has no pickup code hash
    And compartment "C-M1" at station "ST-01" has status "FREE"
    And the compartment "C-M1" at station "ST-01" is open

  Scenario: Pickup code can only be used once
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-ONCE" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I pick up with code "ABCD2345"
    Then the response status is 200
    When I pick up with code "ABCD2345"
    Then the response status is 400
    And the error message is "código de recolha inválido"
    And the parcel "TRK-ONCE" has state "PICKED_UP"
    And compartment "C-M1" at station "ST-01" has status "FREE"

  Scenario: Wrong pickup code does not change the parcel
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-WRONG-CODE" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I pick up with code "ZZZZ9999"
    Then the response status is 400
    And the error message is "código de recolha inválido"
    And the parcel "TRK-WRONG-CODE" has state "READY_FOR_PICKUP"
    And the parcel "TRK-WRONG-CODE" has a pickup code hash
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    And the compartment "C-M1" at station "ST-01" is closed

  Scenario: Invalid pickup code format is rejected
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-FORMAT" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I pick up with code "123"
    Then the response status is 400
    And the error message contains "pickupCode"

  Scenario: Expired parcel cannot be picked up
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-EXPIRED" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    And the parcel "TRK-EXPIRED" is expired
    When I pick up with code "ABCD2345"
    Then the response status is 400
    And the error message contains "não é possível fazer pickup"
    And the parcel "TRK-EXPIRED" has state "EXPIRED"
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    And the compartment "C-M1" at station "ST-01" is closed

  Scenario: Hardware failure does not consume the pickup code
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-HW-PICK" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    And the hardware will fail to open compartments
    When I pick up with code "ABCD2345"
    Then the response status is 503
    And the parcel "TRK-HW-PICK" has state "READY_FOR_PICKUP"
    And the parcel "TRK-HW-PICK" has a pickup code hash
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    When the hardware recovers
    And I pick up with code "ABCD2345"
    Then the response status is 200
    And the parcel "TRK-HW-PICK" has state "PICKED_UP"
    And compartment "C-M1" at station "ST-01" has status "FREE"

  Scenario: Pickup after the deadline is rejected
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-PICK-LATE" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    And the parcel "TRK-PICK-LATE" pickup deadline is "in the past"
    When I pick up with code "ABCD2345"
    Then the response status is 400
    And the error message contains "prazo de recolha expirou"
    And the parcel "TRK-PICK-LATE" has state "READY_FOR_PICKUP"
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
