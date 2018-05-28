package net.adshares.esc.qa.stepdefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.data.UserDataProvider;
import net.adshares.esc.qa.util.*;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;

public class RetrieveFundsStepDefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private TransferUser retriever;
    private TransferUser inactiveUser;
    private String lastResp;
    private long lastEventTs;

    @Given("^user in one node$")
    public void users_in_node() {
        FunctionCaller fc = FunctionCaller.getInstance();
        UserData u = UserDataProvider.getInstance().getUserDataList(1).get(0);
        retriever = new TransferUser();
        retriever.setUserData(u);
        retriever.setStartBalance(fc.getUserAccountBalance(retriever.getUserData()));
        lastEventTs = fc.getLastEventTimestamp(retriever.getUserData()) + 1L;
    }

    @Given("^different user in the same node$")
    public void diff_user_in_same_node() {
        FunctionCaller fc = FunctionCaller.getInstance();
        List<UserData> userDataList = UserDataProvider.getInstance().getUserDataList();

        String retrieverAddress = retriever.getUserData().getAddress();
        inactiveUser = null;
        for (UserData u : userDataList) {
            if (u.isAccountFromSameNode(retrieverAddress) && !retrieverAddress.equals(u.getAddress())) {
                inactiveUser = new TransferUser();
                inactiveUser.setUserData(u);
            }
        }
        Assert.assertNotNull("Cannot find user in the same node", inactiveUser);
        inactiveUser.setStartBalance(fc.getUserAccountBalance(inactiveUser.getUserData()));
    }

    @Given("^different user \\((main|normal)\\) in the different node$")
    public void diff_user_in_diff_node(String type) {
        FunctionCaller fc = FunctionCaller.getInstance();
        List<UserData> userDataList = UserDataProvider.getInstance().getUserDataList();

        String retrieverAddress = retriever.getUserData().getAddress();
        inactiveUser = null;
        for (UserData u : userDataList) {
            if (!u.isAccountFromSameNode(retrieverAddress)) {
                if (("main".equals(type) && u.isMainAccount()) || ("normal".equals(type) && !u.isMainAccount())) {
                    inactiveUser = new TransferUser();
                    inactiveUser.setUserData(u);
                }
            }
        }
        Assert.assertNotNull(String.format("Cannot find %s user in the different node", type), inactiveUser);
        inactiveUser.setStartBalance(fc.getUserAccountBalance(inactiveUser.getUserData()));
    }

    @When("^user requests retrieve$")
    public void user_requests_retrieve() {
        FunctionCaller fc = FunctionCaller.getInstance();

        lastResp = fc.retrieveFunds(retriever.getUserData(), inactiveUser.getUserData().getAddress());

        Assert.assertTrue("Retrieve request not accepted", EscUtils.isTransactionAcceptedByNode(lastResp));
    }

    @When("^user retrieves funds$")
    public void user_retrieves_funds() {
        FunctionCaller fc = FunctionCaller.getInstance();

        lastResp = fc.retrieveFunds(retriever.getUserData(), inactiveUser.getUserData().getAddress());

        Assert.assertTrue("Retrieve funds not accepted", EscUtils.isTransactionAcceptedByNode(lastResp));
    }

    @When("^account is not active for RETRIEVE_DELAY time$")
    public void delay_before_retrieve() {
        // before retrieval account must be inactive for time of:
        // EscConst.BLOCK_DIVIDEND * EscConst.BLOCK_PERIOD (in seconds)
        try {
            Thread.sleep(1000L * EscConst.BLOCK_DIVIDEND * EscConst.BLOCK_PERIOD);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted");
            log.error(e.toString());
        }
    }

    @Then("^retrieve request is not accepted$")
    public void retrieve_request_is_not_accepted() {
        Assert.assertFalse("Retrieve request was accepted", EscUtils.isTransactionAcceptedByNode(lastResp));
    }

    @Then("^after processing time inactive account is empty$")
    public void inactive_account_is_empty() {
        FunctionCaller fc = FunctionCaller.getInstance();


        int loopCnt = 0;
        int loopCntMax = 5;
        do {
            Assert.assertTrue(String.format("Account was not empty in time of %d blocks", loopCntMax), loopCnt < loopCntMax);
            try {
                Thread.sleep(1000L * EscConst.BLOCK_PERIOD);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            loopCnt++;
        } while (BigDecimal.ZERO.compareTo(fc.getUserAccountBalance(inactiveUser)) != 0);
        log.info("Account was empty after {} block(s)", loopCnt);



    }

    @Then("^retriever account is increased by retrieved amount$")
    public void retriever_account_is_increased() {
        FunctionCaller fc = FunctionCaller.getInstance();

        UserData retrieverData = retriever.getUserData();
        LogChecker lc = new LogChecker();
        lc.setResp(fc.getLog(retrieverData));
        Assert.assertTrue("Balance from log is different than that from object", lc.isBalanceFromObjectEqualToArray());

        lastResp = fc.getLog(retrieverData, lastEventTs);
        lc.setResp(lastResp);

        LogFilter lf;
        lf = new LogFilter(false);
        lf.addFilter("type", "retrieve_funds");
        // get sum for other than retrieve_funds
        BigDecimal balanceOtherEvents = lc.getBalanceFromLogArray(lf);
        lf = new LogFilter(true);
        lf.addFilter("type", "retrieve_funds");
        // get sum for retrieve_funds
        BigDecimal balanceRetrieveEvents = lc.getBalanceFromLogArray(lf);
        // check retrieve_funds events
        JsonArray arr = lc.getFilteredLogArray(lf);
        log.info("size = {}", arr.size());
        for (int i = 0; i < 4; i++) {
            JsonObject o = arr.get(i).getAsJsonObject();

            log.info(Utils.jsonPrettyPrint(o.toString()));
            BigDecimal senderFee;
            switch (i) {
                case 0:
                case 2:
                    senderFee = o.get("sender_fee").getAsBigDecimal();
                    Assert.assertEquals(EscConst.RETRIEVE_REQUEST_FEE, senderFee);
                    break;
                case 1:
                    // 1st call confirm
                    break;
                case 3:
                    // 2nd call confirm
                    senderFee = o.get("sender_fee").getAsBigDecimal();
                    BigDecimal senderBalance = o.get("sender_balance").getAsBigDecimal();
                    BigDecimal senderFeeExpected = senderBalance.multiply(EscConst.RETRIEVE_FEE).setScale(11, BigDecimal.ROUND_FLOOR);
                    log.info("senderFeeExpected1: {}", senderFeeExpected.toPlainString());
                    BigDecimal additionalRemoteFee = senderBalance.subtract(senderFeeExpected)
                            .multiply(EscConst.REMOTE_TX_FEE_COEFFICIENT).setScale(11, BigDecimal.ROUND_FLOOR);
                    senderFeeExpected = senderFeeExpected.add(additionalRemoteFee);
                    log.info("senderFee:          {}", senderFee.toPlainString());
                    log.info("senderFeeExpected2: {}", senderFeeExpected.toPlainString());
                    log.info("diff:               {}", senderFee.subtract(senderFeeExpected).toPlainString());
                    Assert.assertEquals(senderFeeExpected, senderFee);
                    break;
            }
        }

        BigDecimal startBalance = retriever.getStartBalance();
        BigDecimal balance = lc.getBalanceFromAccountObject();

        log.info("retriever Start Balance: {}", startBalance);
        log.info("log Retrieve Events:     {}", balanceRetrieveEvents);
        log.info("log Other Events:        {}", balanceOtherEvents);
        log.info("retriever End Balance:   {}", balance);

        Assert.assertEquals(startBalance.add(balanceRetrieveEvents).add(balanceOtherEvents), balance);
    }
}
