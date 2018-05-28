@retrieve_funds
Feature: Retrieve funds

  Scenario: Retrieve funds from inactive remote normal account
    Given user in one node
    And different user (normal) in the different node
    When account is not active for RETRIEVE_DELAY time
    And user requests retrieve
    And account is not active for RETRIEVE_DELAY time
    And user retrieves funds
    Then after processing time inactive account is empty
    And retriever account is increased by retrieved amount

  Scenario: Retrieve funds from inactive remote main account
    Given user in one node
    And different user (main) in the different node
    When account is not active for RETRIEVE_DELAY time
    And user requests retrieve
    And account is not active for RETRIEVE_DELAY time
    And user retrieves funds
    Then after processing time inactive account is empty
    And retriever account is increased by retrieved amount

  Scenario: Retrieve funds from inactive local account
    Given user in one node
    And different user in the same node
    When account is not active for RETRIEVE_DELAY time
    And user requests retrieve but it is not accepted

  Scenario: Check log
    Given user log
