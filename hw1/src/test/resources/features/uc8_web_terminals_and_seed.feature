Feature: UC8 - Web terminals and demo data
  As a user
  I want the terminal pages to be served and the demo data to be reproducible
  So that the system can be demonstrated end to end

  Background:
    Given the system is clean

  Scenario Outline: Static terminal pages are served
    When I request page "<page>"
    Then the response status is 200
    And the response content type starts with "text/html"

    Examples:
      | page          |
      | index.html    |
      | courier.html  |
      | recipient.html |
      | operator.html |
      | tracking.html |

  Scenario: The index page links to operator supervision
    When I request page "index.html"
    Then the response status is 200
    And the response contains "operator.html"

  Scenario: Seed data is idempotent
    Given the seed data has been applied twice
    Then there are 3 stations
    And there are 18 compartments
