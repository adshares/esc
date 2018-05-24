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

import javax.xml.bind.DatatypeConverter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

public class BroadcastStepDefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private List<UserData> userDataList;
    private String broadcastBlockTime;
    private String broadcastMessage;
    /**
     * Maximal message size in bytes
     *
     * Message size is limited by maximum String length. Every byte is encoded as two chars.
     */
    private static final int MESSAGE_MAX_SIZE = Integer.MAX_VALUE / 2 + 1;

    @Given("^set of users$")
    public void set_of_users() {
        userDataList = UserDataProvider.getInstance().getUserDataList();
    }

    @When("^one of them sends broadcast message$")
    public void one_of_them_sends_broadcast_message() {
        broadcastMessage = generateMessage(16);
        BigDecimal feeExpected = getSendBroadcastFee(broadcastMessage);

        UserData u = userDataList.get(0);
        FunctionCaller fc = FunctionCaller.getInstance();
        String resp = fc.broadcast(u, broadcastMessage);
        Assert.assertTrue(EscUtils.isTransactionAcceptedByNode(resp));

        JsonObject o = Utils.convertStringToJsonObject(resp);
        BigDecimal fee = o.getAsJsonObject("tx").get("fee").getAsBigDecimal();
        BigDecimal deduct = o.getAsJsonObject("tx").get("deduct").getAsBigDecimal();
        int blockTimeInt = o.get("current_block_time").getAsInt();
        broadcastBlockTime = Integer.toHexString(blockTimeInt);
        log.info("block time:  {}", broadcastBlockTime);

        log.info("deduct:         {}", deduct.toPlainString());
        log.info("fee:         {}", fee.toPlainString());
        log.info("feeExpected: {}", feeExpected.toPlainString());
        log.info("diff:        {}", feeExpected.subtract(fee).toPlainString());

        Assert.assertEquals("Deduct is not equal to fee.", deduct, fee);
        Assert.assertEquals(feeExpected, fee);
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
     * @param size size of message in bytes
     * @return random message, hexadecimal String (without leading '0x', with even number of characters)
     */
    private String generateMessage(int size) {
        Random random = new Random();
        if (size > MESSAGE_MAX_SIZE) {
            size = MESSAGE_MAX_SIZE;
        }
        byte[] resBuf = new byte[size];
        random.nextBytes(resBuf);
        return DatatypeConverter.printHexBinary(resBuf);
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
