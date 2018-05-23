package net.adshares.esc.qa.stepdefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.data.UserDataProvider;
import net.adshares.esc.qa.util.EscConst;
import net.adshares.esc.qa.util.EscUtils;
import net.adshares.esc.qa.util.FunctionCaller;
import net.adshares.esc.qa.util.Utils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

public class BroadcastStepDefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private List<UserData> userDataList;
    private String broadcastBlockTime;
    private String broadcastMessage;

    @Given("^set of users$")
    public void set_of_users() {
        userDataList = UserDataProvider.getInstance().getUserDataList();
    }

    @When("^one of them sends broadcast message$")
    public void one_of_them_sends_broadcast_message() {
        broadcastMessage = generateMessage();
        BigDecimal feeExpected = getSendBroadcastFee(broadcastMessage);

        UserData u = userDataList.get(0);
        FunctionCaller fc = FunctionCaller.getInstance();
        String resp = fc.broadcast(u, broadcastMessage);
        Assert.assertTrue(EscUtils.isTransactionAcceptedByNode(resp));

        JsonObject o = Utils.convertStringToJsonObject(resp);
        BigDecimal fee = o.getAsJsonObject("tx").get("fee").getAsBigDecimal();
        int blockTimeInt = o.get("current_block_time").getAsInt();
        broadcastBlockTime = Integer.toHexString(blockTimeInt);
        log.info("block time:  {}", broadcastBlockTime);

        log.info("fee:         {}", fee.toPlainString());
        log.info("feeExpected: {}", feeExpected.toPlainString());
        log.info("diff:        {}", feeExpected.subtract(fee).toPlainString());
        Assert.assertEquals(fee, feeExpected);
    }

    @Then("^all of them can read it$")
    public void all_of_them_can_read_it() {
        try {
            Thread.sleep(1000L * EscConst.BLOCK_PERIOD);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted");
            log.error(e.toString());
        }
        FunctionCaller fc = FunctionCaller.getInstance();

        String resp;
        String msg;
        for (UserData u : userDataList) {
            resp = fc.getBroadcast(u, broadcastBlockTime);

            JsonObject o = Utils.convertStringToJsonObject(resp);
            JsonArray broadcastArr = o.getAsJsonArray("broadcast");
            int size = broadcastArr.size();
            log.info("size {}", size);
            for (int i = 0; i < size; i++) {
                msg = broadcastArr.get(i).getAsJsonObject().get("message").getAsString();
                log.info("{} message: {}", i, msg);
                if (broadcastMessage.equals(msg)) {
                    break;
                }
            }
        }
    }

    /**
     * Generates random message.
     *
     * @return random message, hexadecimal String (without leading '0x', with even number of characters)
     */
    private static String generateMessage() {
        // message maximal length
        int maxLen = 16000;

        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        int msgLen = r.nextInt(maxLen);
        if (msgLen % 2 == 1) {
            msgLen--;
        }
        for (int i = 0; i < msgLen; i++) {
            sb.append(Integer.toHexString(r.nextInt(16)));
        }
        return sb.toString();
    }

    /**
     * Calculates fee for broadcasting message.
     *
     * @param message message
     * @return fee for broadcasting message
     */
    private BigDecimal getSendBroadcastFee(String message) {
        int len = message.length();

        Assert.assertEquals("Not even length of message. Current length = " + len, 0, len % 2);
        int sizeBytes = len / 2;
        return EscConst.BROADCAST_FEE_COEFFICIENT.multiply(new BigDecimal(sizeBytes)).add(EscConst.MIN_TX_FEE);
    }
}
