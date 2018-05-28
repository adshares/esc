package net.adshares.esc.qa.stepdefs;

import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.data.UserDataProvider;
import net.adshares.esc.qa.util.EscUtils;
import net.adshares.esc.qa.util.FunctionCaller;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Cucumber steps definitions for transaction to non-existent user/node tests
 */
public class NonExistentUserNodeStepDefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private TransferUser sender;
    private String lastResp;

    @Given("^1 user$")
    public void one_user() {
        UserData u = UserDataProvider.getInstance().getUserDataList(1).get(0);

        FunctionCaller fc = FunctionCaller.getInstance();
        BigDecimal balance = fc.getUserAccountBalance(u);

        sender = new TransferUser();
        sender.setUserData(u);
        sender.setStartBalance(balance);
    }

    @When("^sends 0.00000000001 ADST to user in non-existent node$")
    public void sender_sends_to_non_existent_node() {
        sendOneWrapper(getUserAddressInNonExistentNode());
    }

    @When("^sends 0.00000000001 ADST to non-existent user in node$")
    public void sender_sends_to_non_existent_user() {
        sendOneWrapper(getNonExistentUserAddressInNode());
    }

    @When("^retrieves from user in non-existent node$")
    public void retrieves_from_non_existent_node() {
        retrieveFundsWrapper(getUserAddressInNonExistentNode());
    }

    @When("^retrieves from non-existent user in node$")
    public void retrieves_from_non_existent_user() {
        retrieveFundsWrapper(getNonExistentUserAddressInNode());
    }

    @Then("^transfer to invalid address is rejected$")
    public void transfer_is_rejected() {
        log.info("Error: \"{}\"", EscUtils.getErrorDescription(lastResp));
        Assert.assertEquals("Sender balance changed", 0, sender.getExpBalance().compareTo(sender.getStartBalance()));
        Assert.assertFalse("Transfer to invalid address was accepted", EscUtils.isTransactionAcceptedByNode(lastResp));
    }

    private void sendOneWrapper(String address) {
        FunctionCaller fc = FunctionCaller.getInstance();
        sender.setExpBalance(sender.getStartBalance());
        lastResp = fc.sendOne(sender.getUserData(), address, "0.00000000001");
    }

    private void retrieveFundsWrapper(String address) {
        FunctionCaller fc = FunctionCaller.getInstance();
        sender.setExpBalance(sender.getStartBalance());
        lastResp = fc.retrieveFunds(sender.getUserData(), address);
    }

    private String getNonExistentUserAddressInNode() {
        return "0001-FFFFFFFF-XXXX";
    }

    private String getUserAddressInNonExistentNode() {
        return "FFFF-00000000-XXXX";
    }

}