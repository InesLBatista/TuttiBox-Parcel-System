Feature: UC2 - Register a parcel
  As a partner
  I want to register a parcel for a station and compartment size
  So that the parcel can be delivered to a locker

  Background:
    Given the system is clean

  Scenario: Registering an M parcel reserves the smallest compatible compartment
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-01" of size "S"
    And a free compartment "C-M1" at station "ST-01" of size "M"
    And a free compartment "C-L1" at station "ST-01" of size "L"
    When I register a parcel of size "M" for "client@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And the response contains a tracking code
    And the assigned compartment of the last parcel is "C-M1"
    And the assigned station of the last parcel is "ST-01"
    And the last parcel has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"

  Scenario: Registering an S parcel uses an S compartment even when larger ones are free
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-01" of size "S"
    And a free compartment "C-M1" at station "ST-01" of size "M"
    And a free compartment "C-L1" at station "ST-01" of size "L"
    When I register a parcel of size "S" for "client@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And the assigned compartment of the last parcel is "C-S1"
    And compartment "C-S1" at station "ST-01" has status "RESERVED"

  Scenario: No compatible compartment returns conflict
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-01" of size "S"
    When I register a parcel of size "L" for "client@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 409
    And the error message contains "sem compartimentos disponíveis"
    And the system contains 0 parcels

  Scenario: Unknown station returns not found
    When I register a parcel of size "M" for "client@example.com" from partner "P-01" at station "ST-MISSING"
    Then the response status is 404
    And the error message is "estação não encontrada: ST-MISSING"

  Scenario: Invalid compartment size returns bad request
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    When I register a parcel of size "X" for "client@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 400
    And the error message contains "compartmentSize"

  Scenario: Blank station returns bad request
    When I register a parcel of size "M" for "client@example.com" from partner "P-01" at station ""
    Then the response status is 400
    And the error message contains "stationId"

  Scenario: Repeating a registration creates an independent parcel
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-01" of size "S"
    And a free compartment "C-S2" at station "ST-01" of size "S"
    When I register a parcel of size "S" for "first@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And I remember the last tracking code as "first"
    When I register a parcel of size "S" for "second@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And the two registrations produced different tracking codes
    And the system contains 2 parcels
    And compartment "C-S1" at station "ST-01" has status "RESERVED"
    And compartment "C-S2" at station "ST-01" has status "RESERVED"

  Scenario: Repeating after cancellation creates a new parcel
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-01" of size "S"
    When I register a parcel of size "S" for "first@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And I remember the last tracking code as "first"
    When I cancel the parcel remembered as "first" by actor "P-01"
    Then the response status is 200
    And the parcel remembered as "first" has state "CANCELLED"
    When I register a parcel of size "S" for "second@example.com" from partner "P-01" at station "ST-01"
    Then the response status is 201
    And the two registrations produced different tracking codes
    And the system contains 2 parcels
    And compartment "C-S1" at station "ST-01" has status "RESERVED"

  Scenario: Concurrent registrations do not double-book the last compartment
    Given a station "ST-CONC" named "Estação Concorrência" at 40.6305, -8.6571
    And a free compartment "C-S1" at station "ST-CONC" of size "S"
    When two partners register size "S" at station "ST-CONC" concurrently
    Then exactly one registration succeeds
    And one registration fails with a conflict
    And the system contains 1 parcel
    And compartment "C-S1" at station "ST-CONC" has status "RESERVED"
