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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BroadcastStepDefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private List<UserData> userDataList;
    private Set<BroadcastMessageData> bmdSet;
    private String lastResp;

    /**
     * Maximum message size in bytes
     * <p>
     * Message size is limited by maximum String length. Every byte is encoded as two chars.
     */
    private static final int MESSAGE_MAX_SIZE = Integer.MAX_VALUE / 2 + 1;

    @Given("^set of users$")
    public void set_of_users() {
        userDataList = UserDataProvider.getInstance().getUserDataList();
    }

    @When("^one of them sends valid broadcast message which size is (\\d+) byte\\(s\\)$")
    public void one_of_them_sends_broadcast_message(int messageSize) {
        UserData u = userDataList.get(0);
        FunctionCaller fc = FunctionCaller.getInstance();

        BroadcastMessageData bmd = sendBroadcastMessageData(u, messageSize);
        bmdSet = new HashSet<>();
        bmdSet.add(bmd);
    }

    @When("^one of them sends broadcast message which size is (\\d+) bytes$")
    public void one_of_them_sends_broadcast_message_len(int messageSize) {
        UserData u = userDataList.get(0);
        FunctionCaller fc = FunctionCaller.getInstance();
        lastResp = fc.broadcast(u, generateMessage(messageSize));
    }

    @When("^one of them sends many broadcast messages$")
    public void one_of_them_send_many_broadcast_messages() {
        FunctionCaller fc = FunctionCaller.getInstance();
        UserData u = userDataList.get(0);

        int messageSize = 1;
        bmdSet = new HashSet<>();
        do {
            BroadcastMessageData bmd = sendBroadcastMessageData(u, messageSize);
            bmdSet.add(bmd);

            messageSize *= 2;
        } while (messageSize <= EscConst.BROADCAST_MESSAGE_MAX_SIZE);
    }

    /**
     * Broadcast random message.
     *
     * @param userData    user data
     * @param messageSize size of message in bytes
     * @return BroadcastMessageData object
     */
    private BroadcastMessageData sendBroadcastMessageData(UserData userData, int messageSize) {
        FunctionCaller fc = FunctionCaller.getInstance();

        String message = generateMessage(messageSize);
        BigDecimal feeExpected = getBroadcastFee(message);

        String resp = fc.broadcast(userData, message);
        Assert.assertTrue(EscUtils.isTransactionAcceptedByNode(resp));

        JsonObject o = Utils.convertStringToJsonObject(resp);
        BigDecimal fee = o.getAsJsonObject("tx").get("fee").getAsBigDecimal();
        BigDecimal deduct = o.getAsJsonObject("tx").get("deduct").getAsBigDecimal();
        int blockTimeInt = o.get("current_block_time").getAsInt();
        String blockTime = Integer.toHexString(blockTimeInt);
        log.info("block time:  {}", blockTime);

        log.info("deduct:         {}", deduct.toPlainString());
        log.info("fee:         {}", fee.toPlainString());
        log.info("feeExpected: {}", feeExpected.toPlainString());
        log.info("diff:        {}", feeExpected.subtract(fee).toPlainString());

        Assert.assertEquals("Deduct is not equal to fee.", deduct, fee);
        Assert.assertEquals(feeExpected, fee);

        return new BroadcastMessageData(message, blockTime);
    }

    @Then("^all of them can read it$")
    public void all_of_them_can_read_it() {
        FunctionCaller fc = FunctionCaller.getInstance();
        // waiting time for message in block periods
        int delay = 0;
        // maximum waiting time for message in block periods
        int delayMax = 2;

        for (UserData u : userDataList) {

            for (BroadcastMessageData bmd : bmdSet) {
                String message = bmd.getMessage();
                boolean isMessageReceived = false;
                String blockTime = bmd.getBlockTime();
                int nextBlockCheckAttempt = 0;
                int nextBlockCheckAttemptMax = 2;
                String resp;
                JsonObject o;

                boolean isError;
                do {
                    resp = fc.getBroadcast(u, blockTime);
                    o = Utils.convertStringToJsonObject(resp);

                    isError = o.has("error");
                    if (isError) {
                        String err = o.get("error").getAsString();
                        log.warn("get_broadcast error: {}", err);

                        if (EscConst.Error.BROADCAST_NOT_READY.equals(err)) {
                            Assert.assertTrue(delay < delayMax);
                            log.info("wait another block");
                            waitForBlock();
                            delay++;
                        } else if (EscConst.Error.BROADCAST_NO_FILE_TO_SEND.equals(err)) {
                            Assert.assertTrue(nextBlockCheckAttempt < nextBlockCheckAttemptMax);
                            log.info("try check next block");
                            blockTime = EscUtils.getNextBlock(blockTime);
                            nextBlockCheckAttempt++;
                        } else {
                            Assert.fail("No message");
                        }
                    }
                } while (isError);

                JsonArray broadcastArr = o.getAsJsonArray("broadcast");
                int size = broadcastArr.size();
                log.info("size {}", size);
                for (int i = 0; i < size; i++) {
                    String msgRec = broadcastArr.get(i).getAsJsonObject().get("message").getAsString();
                    if (message.equals(msgRec)) {
                        log.info("received message");
                        isMessageReceived = true;
                        break;
                    }
                }
                Assert.assertTrue("Didn't receive message.", isMessageReceived);
            }

        }
    }

    private void waitForBlock() {
        try {
            Thread.sleep(1000L * EscConst.BLOCK_PERIOD);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted");
            log.error(e.toString());
        }
    }

    @Then("^message is rejected$")
    public void message_is_rejected() {
        JsonObject o = Utils.convertStringToJsonObject(lastResp);
        if (o.has("error")) {
            String err = o.get("error").getAsString();
            log.info("Message was rejected with error: {}", err);
        } else {
            Assert.assertFalse(EscUtils.isTransactionAcceptedByNode(lastResp));
            log.info("Message was rejected: not accepted by node");
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
    private BigDecimal getBroadcastFee(String message) {
        int len = message.length();

        Assert.assertEquals("Not even length of message. Current length = " + len, 0, len % 2);
        int sizeBytes = len / 2;

        BigDecimal fee = EscConst.MIN_TX_FEE;
        if (sizeBytes > 32) {
            fee = fee.add(EscConst.BROADCAST_FEE_PER_BYTE.multiply(new BigDecimal(sizeBytes - 32)));
        }
        return fee;
    }

    class BroadcastMessageData {
        private String message;
        private String blockTime;

        BroadcastMessageData(String message, String blockTime) {
            this.message = message;
            this.blockTime = blockTime;
        }

        String getMessage() {
            return message;
        }

        String getBlockTime() {
            return blockTime;
        }
    }
}
