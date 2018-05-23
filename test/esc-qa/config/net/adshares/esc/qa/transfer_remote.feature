@transfer
@transfer_remote
Feature: remote transfers

    Scenario: Send 100 ADST
        Given 2 users in different node
        When sender sends 100 ADST to receiver
        And wait for balance update
        Then receiver balance is increased by sent amount
        And sender balance is decreased by sent amount and fee

    Scenario: Send minimum amount of ADST
        Given 2 users in different node
        When sender sends 0.00000000001 ADST to receiver
        And wait for balance update
        Then receiver balance is increased by sent amount
        And sender balance is decreased by sent amount and fee

    Scenario: Send non existing amount of ADST
        Given 2 users in different node
        When sender sends 40000000 ADST to receiver
        And wait for balance update
        Then receiver balance is as expected
        And sender balance is as expected

    Scenario: Send many
        Given 3 users in different node
        When sender sends 100 ADST to receivers
        And wait for balance update
        Then receiver balance is increased by sent amount
        And sender balance is decreased by sent amount and fee

    Scenario: Send all collected funds (no fee included)
        Given 2 users in different node
        When sender sends all to receiver (fee is not included)
        And wait for balance update
        Then receiver balance is as expected
        And sender balance is as expected

    Scenario: Send all collected funds (fee included)
        Given 2 users in different node
        When sender sends all to receiver (fee is included)
        And wait for balance update
        Then receiver balance is increased by sent amount
        And sender balance is decreased by sent amount and fee

    Scenario: Check log
        Given user log
