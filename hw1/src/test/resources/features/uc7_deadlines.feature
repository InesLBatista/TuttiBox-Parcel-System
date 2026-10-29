Feature: UC7 - Parcel deadlines
  As the system
  I want to cancel or expire parcels that exceeded their deadlines
  So that compartments are not blocked indefinitely

  Background:
    Given the system is clean

  Scenario: Expired drop-off is cancelled and the compartment is released
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-LATE-DROP" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    And the parcel "TRK-LATE-DROP" drop-off deadline is "in the past"
    When the system applies deadlines
    Then the parcel "TRK-LATE-DROP" has state "CANCELLED"
    And compartment "C-M1" at station "ST-01" has status "FREE"
    When I request the history of parcel "TRK-LATE-DROP"
    Then the history contains a transition from "CREATED" to "CANCELLED" by "SYSTEM"

  Scenario: Expired pickup is expired and the compartment remains occupied
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a ready parcel "TRK-LATE-PICK" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" with pickup code "ABCD2345"
    And the parcel "TRK-LATE-PICK" pickup deadline is "in the past"
    When the system applies deadlines
    Then the parcel "TRK-LATE-PICK" has state "EXPIRED"
    And compartment "C-M1" at station "ST-01" has status "OCCUPIED"
    When I request the history of parcel "TRK-LATE-PICK"
    Then the history contains a transition from "READY_FOR_PICKUP" to "EXPIRED" by "SYSTEM"

  Scenario: Parcels within the drop-off deadline are unchanged
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-ON-TIME" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    And the parcel "TRK-ON-TIME" drop-off deadline is "in the future"
    When the system applies deadlines
    Then the parcel "TRK-ON-TIME" has state "CREATED"
    And compartment "C-M1" at station "ST-01" has status "RESERVED"

  Scenario: The scheduler applies the same deadline rules
    Given a station "ST-01" named "Estação Central" at 40.6305, -8.6571
    And a parcel "TRK-SCHEDULER" for "client@example.com" with size "M" at station "ST-01" in compartment "C-M1" in state "CREATED"
    And the parcel "TRK-SCHEDULER" drop-off deadline is "in the past"
    When the deadline scheduler runs
    Then the parcel "TRK-SCHEDULER" has state "CANCELLED"
    And compartment "C-M1" at station "ST-01" has status "FREE"
