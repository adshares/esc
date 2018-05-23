@broadcast
Feature: Broadcast message

  Scenario: Broadcast message
    Given set of users
    When one of them sends broadcast message
    Then all of them can read it

  Scenario: Check log
    Given user log
