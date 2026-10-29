Feature: UC1 - Find the nearest locker
  As a recipient
  I want to find nearby stations for an address
  So that I can choose where to receive my parcel

  Background:
    Given the system is clean

  Scenario: Nearest stations are ordered and expose free capacity
    Given a station "ST-NEAR" named "Estação Perto" at 40.6305, -8.6571
    And a station "ST-FAR" named "Estação Longe" at 40.7000, -8.7000
    And a free compartment "C-S1" at station "ST-NEAR" of size "S"
    And a free compartment "C-S2" at station "ST-NEAR" of size "S"
    And a free compartment "C-M1" at station "ST-NEAR" of size "M"
    And a free compartment "C-L1" at station "ST-FAR" of size "L"
    And the address "Anadia" resolves to 40.6305, -8.6571
    When I search for stations near "Anadia"
    Then the response status is 200
    And the response is a JSON array with 2 entries
    And station "ST-NEAR" is at position 0
    And station "ST-NEAR" has 2 small, 1 medium and 0 large compartments available
    And station "ST-FAR" is at position 1
    And station "ST-FAR" has 0 small, 0 medium and 1 large compartments available

  Scenario: Unknown address returns an empty list
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    When I search for stations near "morada inexistente"
    Then the response status is 200
    And the response is an empty JSON array

  Scenario Outline: Geocoding failures are translated into API errors
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And the geocoding provider will fail with "<failure>"
    When I search for stations near "Anadia"
    Then the response status is <status>
    And the error message is "<message>"

    Examples:
      | failure          | status | message                                       |
      | unavailable      | 503    | geocoding service is unavailable              |
      | timeout          | 504    | geocoding service timed out                   |
      | rate limited     | 503    | geocoding service usage limit exceeded        |
      | invalid response | 502    | geocoding service returned an invalid response |
      | provider error   | 502    | geocoding service returned an error           |
