Feature: UC5 - Track a parcel and manage lifecycle transitions
  As a recipient or partner
  I want to inspect the parcel status and history
  So that I can follow the parcel lifecycle

  Background:
    Given the system is clean

  Scenario: Status and history expose the parcel lifecycle
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-TRACK" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I request the status of parcel "TRK-TRACK"
    Then the response status is 200
    And the response body is "READY_FOR_PICKUP"
    When I request the history of parcel "TRK-TRACK"
    Then the response status is 200
    And the history has 2 transitions
    And the history contains a transition from "CREATED" to "READY_FOR_PICKUP" by "COURIER_BDD"

  Scenario: Unknown parcel status returns not found
    When I request the status of parcel "TRK-MISSING"
    Then the response status is 404
    And the error message is "encomenda não foi encontrada: TRK-MISSING"

  Scenario: Unknown parcel history returns not found
    When I request the history of parcel "TRK-MISSING"
    Then the response status is 404
    And the error message is "encomenda não foi encontrada: TRK-MISSING"

  Scenario: Partner can cancel a created parcel
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-CANCEL" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I cancel parcel "TRK-CANCEL" by actor "PARTNER-1"
    Then the response status is 200
    And the response field "currentState" is "CANCELLED"
    And the parcel "TRK-CANCEL" has state "CANCELLED"
    And compartment "C-M1" at station "ST-01" has status "FREE"
    When I request the history of parcel "TRK-CANCEL"
    Then the history contains a transition from "CREATED" to "CANCELLED" by "PARTNER-1"

  Scenario: A cancelled parcel cannot be cancelled again
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-CANCEL-2" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    When I cancel parcel "TRK-CANCEL-2" by actor "PARTNER-1"
    Then the response status is 200
    When I cancel parcel "TRK-CANCEL-2" by actor "PARTNER-1"
    Then the response status is 400
    And the error message contains "só pode ser cancelado"
    And the parcel "TRK-CANCEL-2" has state "CANCELLED"

  Scenario: A ready parcel can expire and then be returned
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-EXPIRE" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    When I expire parcel "TRK-EXPIRE" by actor "SYSTEM"
    Then the response status is 200
    And the response field "currentState" is "EXPIRED"
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    When I return parcel "TRK-EXPIRE" by actor "OPERATOR-1"
    Then the response status is 200
    And the response field "currentState" is "RETURNED"
    And compartment "C-M1" at station "ST-01" has status "FREE"
    When I request the history of parcel "TRK-EXPIRE"
    Then the history contains a transition from "READY_FOR_PICKUP" to "EXPIRED" by "SYSTEM"
    And the history contains a transition from "EXPIRED" to "RETURNED" by "OPERATOR-1"
