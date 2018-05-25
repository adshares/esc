@broadcast
Feature: Broadcast message

  Scenario Outline: Broadcast <msg_size> byte(s) message (valid)
    Given set of users
    When one of them sends valid broadcast message which size is <msg_size> byte(s)
    Then all of them can read it
    Examples:
      |msg_size|
      |1|
      |2|
      |10|
      |32000|

  Scenario Outline: Broadcast <msg_size> bytes message (invalid size)
    Given set of users
    When one of them sends broadcast message which size is <msg_size> bytes
    Then message is rejected
    Examples:
    |msg_size|
    |0|
    |32001|

  Scenario: Broadcast many messages
    Given set of users
    When one of them sends many broadcast messages
    Then all of them can read it

  Scenario: Check log
    Given user log
