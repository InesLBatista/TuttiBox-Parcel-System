Feature: UC6 - Operator supervision
  As an operator
  I want to supervise parcels and compartments
  So that I can handle expired parcels and out-of-service lockers

  Background:
    Given the system is clean

  Scenario: List parcels and filter by station and state
    Given a station "ST-A" named "Estação A" at 40.6305, -8.6571
    And a station "ST-B" named "Estação B" at 40.6405, -8.6538
    And a parcel "TRK-A-READY" for "a@example.com" with size "M" at station "ST-A" in compartment "C-A1" in state "READY_FOR_PICKUP"
    And a parcel "TRK-B-READY" for "b@example.com" with size "M" at station "ST-B" in compartment "C-B1" in state "READY_FOR_PICKUP"
    And a parcel "TRK-A-CREATED" for "c@example.com" with size "S" at station "ST-A" in compartment "C-A2" in state "CREATED"
    When I list all parcels
    Then the response status is 200
    And the response is a JSON array with 3 entries
    When I list parcels for station "ST-A"
    Then the response status is 200
    And the response is a JSON array with 2 entries
    And the response array contains tracking code "TRK-A-READY"
    And the response array contains tracking code "TRK-A-CREATED"
    When I list parcels in state "READY_FOR_PICKUP"
    Then the response status is 200
    And the response is a JSON array with 2 entries
    And the response array contains tracking code "TRK-A-READY"
    And the response array contains tracking code "TRK-B-READY"

  Scenario: Unknown station filter returns not found
    When I list parcels for station "UNKNOWN"
    Then the response status is 404
    And the error message is "estação não encontrada: UNKNOWN"

  Scenario: List expired parcels and filter by station
    Given a station "ST-A" named "Estação A" at 40.6305, -8.6571
    And a station "ST-B" named "Estação B" at 40.6405, -8.6538
    And a parcel "TRK-A-EXPIRED" for "a@example.com" with size "M" at station "ST-A" in compartment "C-A1" in state "EXPIRED"
    And a parcel "TRK-B-EXPIRED" for "b@example.com" with size "M" at station "ST-B" in compartment "C-B1" in state "EXPIRED"
    When I list expired parcels
    Then the response status is 200
    And the response is a JSON array with 2 entries
    When I list expired parcels for station "ST-A"
    Then the response status is 200
    And the response is a JSON array with 1 entries
    And the response array contains tracking code "TRK-A-EXPIRED"
    And the response array does not contain tracking code "TRK-B-EXPIRED"

  Scenario: Removing an expired parcel releases its compartment
    Given a station "ST-REMOVE" named "Estação Remoção" at 40.6305, -8.6571
    And a parcel "TRK-REMOVE" for "client@example.com" with size "M" at station "ST-REMOVE" in compartment "C-1" in state "EXPIRED"
    When I remove expired parcel "TRK-REMOVE" by operator "OPERATOR-1"
    Then the response status is 200
    And the response field "trackingCode" is "TRK-REMOVE"
    And the response field "currentState" is "RETURNED"
    And the response field "assignedCompartmentCode" is "C-1"
    And compartment "C-1" at station "ST-REMOVE" has status "FREE"

  Scenario: Cannot remove a parcel before it expires
    Given a station "ST-READY" named "Estação Pronta" at 40.6305, -8.6571
    And a parcel "TRK-READY" for "client@example.com" with size "M" at station "ST-READY" in compartment "C-1" in state "READY_FOR_PICKUP"
    When I remove expired parcel "TRK-READY" by operator "OPERATOR-1"
    Then the response status is 400
    And the error message contains "expiraram"

  Scenario: Supervision operations require an actor
    Given a station "ST-ACTOR" named "Estação Ator" at 40.6305, -8.6571
    And a parcel "TRK-ACTOR" for "client@example.com" with size "M" at station "ST-ACTOR" in compartment "C-1" in state "EXPIRED"
    When I remove expired parcel "TRK-ACTOR" by operator " "
    Then the response status is 400
    And the error message is "o ator é obrigatório"

  Scenario: Compartment can go out of service and back in service
    Given a station "ST-SERVICE" named "Estação Serviço" at 40.6305, -8.6571
    And a free compartment "C-1" at station "ST-SERVICE" of size "M"
    When I put compartment "C-1" at station "ST-SERVICE" out of service by operator "OPERATOR-1"
    Then the response status is 200
    And the response field "code" is "C-1"
    And the response field "stationCode" is "ST-SERVICE"
    And the response field "status" is "OUT_OF_SERVICE"
    When I list compartments for station "ST-SERVICE"
    Then the response status is 200
    And the response array contains compartment "C-1" with status "OUT_OF_SERVICE"
    When I put compartment "C-1" at station "ST-SERVICE" in service by operator "OPERATOR-1"
    Then the response status is 200
    And the response field "status" is "FREE"

  Scenario: Out-of-service compartments are not allocated
    Given a station "ST-FULL" named "Estação Cheia" at 40.6305, -8.6571
    And an out-of-service compartment "C-M1" at station "ST-FULL" of size "M"
    When I register a parcel of size "M" for "client@example.com" from partner "P-01" at station "ST-FULL"
    Then the response status is 409
    And the system contains 0 parcels

  Scenario: Occupied compartment cannot go out of service
    Given a station "ST-OCCUPIED" named "Estação Ocupada" at 40.6305, -8.6571
    And an occupied compartment "C-1" at station "ST-OCCUPIED" of size "M"
    When I put compartment "C-1" at station "ST-OCCUPIED" out of service by operator "OPERATOR-1"
    Then the response status is 409
    And the error message contains "OCCUPIED"

  Scenario: Free compartment cannot go back in service
    Given a station "ST-FREE" named "Estação Livre" at 40.6305, -8.6571
    And a free compartment "C-1" at station "ST-FREE" of size "M"
    When I put compartment "C-1" at station "ST-FREE" in service by operator "OPERATOR-1"
    Then the response status is 409
    And the error message contains "não está fora de serviço"

  Scenario: Unknown compartment or station returns not found
    When I put compartment "MISSING" at station "ST-404" out of service by operator "OPERATOR-1"
    Then the response status is 404
    When I list compartments for station "MISSING"
    Then the response status is 404
    And the error message is "estação não encontrada: MISSING"
