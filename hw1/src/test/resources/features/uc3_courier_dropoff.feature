Feature: UC3 - Courier drop-off
  As a courier
  I want to open the assigned compartment and confirm that the door is closed
  So that the parcel becomes ready for recipient pickup

  Background:
    Given the system is clean

  Scenario: Drop-off opens the assigned compartment and confirmation makes the parcel ready
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-DROP" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I request drop-off for parcel "TRK-DROP" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 200
    And the response field "currentState" is "CREATED"
    And the compartment "C-M1" at station "ST-01" is open
    And the parcel "TRK-DROP" has no pickup code hash
    And no pickup code notification was sent
    When I confirm drop-off for parcel "TRK-DROP" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 200
    And the response field "currentState" is "READY_FOR_PICKUP"
    And the response does not contain field "pickupCodeHash"
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    And the compartment "C-M1" at station "ST-01" is closed
    And the parcel "TRK-DROP" has a pickup code hash
    And a pickup code notification was sent to "client@example.com" for parcel "TRK-DROP"

  Scenario: Drop-off with the wrong compartment is rejected
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-WRONG" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I request drop-off for parcel "TRK-WRONG" at station "ST-01" compartment "C-M2" by courier "COURIER-1"
    Then the response status is 400
    And the error message contains "não corresponde ao compartimento atribuído"
    And the parcel "TRK-WRONG" has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"
    And no pickup code notification was sent

  Scenario: Drop-off with a compartment that is too small is rejected
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a reserved compartment "C-S1" at station "ST-01" of size "S"
    And a parcel "TRK-SMALL" for "client@example.com" with size "L" at station "ST-01" in compartment "C-S1" in state "CREATED"
    When I request drop-off for parcel "TRK-SMALL" at station "ST-01" compartment "C-S1" by courier "COURIER-1"
    Then the response status is 400
    And the error message contains "tamanho do compartimento S é muito pequeno"
    And the parcel "TRK-SMALL" has state "CREATED"

  Scenario: Hardware failure while opening keeps the parcel created
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-OPEN-FAIL" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    And the hardware will fail to open compartments
    When I request drop-off for parcel "TRK-OPEN-FAIL" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 503
    And the parcel "TRK-OPEN-FAIL" has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"
    And no pickup code notification was sent

  Scenario: Open door during confirmation keeps the parcel created
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-DOOR-OPEN" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I request drop-off for parcel "TRK-DOOR-OPEN" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 200
    When the hardware will report the door as still open
    And I confirm drop-off for parcel "TRK-DOOR-OPEN" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 503
    And the parcel "TRK-DOOR-OPEN" has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"
    And no pickup code notification was sent

  Scenario: Hardware failure while closing keeps the parcel created
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-CLOSE-FAIL" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I request drop-off for parcel "TRK-CLOSE-FAIL" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 200
    When the hardware will fail to confirm closing
    And I confirm drop-off for parcel "TRK-CLOSE-FAIL" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 503
    And the parcel "TRK-CLOSE-FAIL" has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"
    And no pickup code notification was sent

  Scenario: Drop-off after the deadline is rejected
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-LATE" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    And the parcel "TRK-LATE" drop-off deadline is "in the past"
    When I request drop-off for parcel "TRK-LATE" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 400
    And the error message contains "prazo de drop-off expirou"
    And the parcel "TRK-LATE" has state "CREATED"

  Scenario: Drop-off of an unknown parcel returns not found
    When I request drop-off for parcel "TRK-MISSING" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 404
    And the error message is "encomenda não foi encontrada: TRK-MISSING"

  Scenario: Confirmation of an unknown parcel returns not found
    When I confirm drop-off for parcel "TRK-MISSING" at station "ST-01" compartment "C-M1" by courier "COURIER-1"
    Then the response status is 404
    And the error message is "encomenda não foi encontrada: TRK-MISSING"
