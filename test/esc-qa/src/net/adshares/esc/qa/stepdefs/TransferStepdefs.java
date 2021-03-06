package net.adshares.esc.qa.stepdefs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.data.UserDataProvider;
import net.adshares.esc.qa.util.EscConst;
import net.adshares.esc.qa.util.FunctionCaller;
import net.adshares.esc.qa.util.LogFilter;
import net.adshares.esc.qa.util.Utils;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

/**
 * Cucumber steps definitions for transfer tests
 */
public class TransferStepdefs {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private TransferUser txSender;
    private List<TransferUser> txReceivers;


    @Given("^(\\d+) users in (same|different) node$")
    public void user_in_same_node(int userCount, String nodeType) {
        boolean isSameNode = "same".equals(nodeType);
        List<UserData> userDataList;
        if (isSameNode) {
            userDataList = UserDataProvider.getInstance().getUserDataList(userCount, true);
        } else {
            userDataList = UserDataProvider.getInstance().getUserDataFromDifferentNodes(userCount);
        }

        FunctionCaller fc = FunctionCaller.getInstance();
        BigDecimal maxBalance = BigDecimal.ZERO;
        int maxBalanceIndex = -1;
        for (int i = 0; i < userDataList.size(); i++) {
            BigDecimal balance = fc.getUserAccountBalance(userDataList.get(i));
            if (balance.compareTo(maxBalance) > 0) {
                maxBalance = balance;
                maxBalanceIndex = i;
            }
        }

        Assert.assertNotEquals("No user with positive balance.", maxBalanceIndex, -1);

        txReceivers = new ArrayList<>();
        for (int i = 0; i < userDataList.size(); i++) {
            UserData userData = userDataList.get(i);
            TransferUser tu = new TransferUser();
            tu.setUserData(userData);
            tu.setStartBalance(fc.getUserAccountBalance(userData));

            if (i == maxBalanceIndex) {
                txSender = tu;
            } else {
                txReceivers.add(tu);
            }
        }
    }

    @When("^sender sends (\\d+(\\.\\d+)?) ADST to receiver[s]?$")
    public void send_adst(String txAmount, String decimalPart) {
        FunctionCaller fc = FunctionCaller.getInstance();
        UserData sender = txSender.getUserData();
        String senderAddress = sender.getAddress();

        int receiversCount = txReceivers.size();
        // amount to single receiver
        BigDecimal amount = new BigDecimal(txAmount).setScale(11, BigDecimal.ROUND_FLOOR);
        // amount from sender to all receivers
        BigDecimal amountOut = amount.multiply(new BigDecimal(receiversCount)).setScale(11, BigDecimal.ROUND_FLOOR);
        // transfer fee payed by sender
        BigDecimal fee;
        String jsonResp;
        if (receiversCount > 1) {
            // send many
            Map<String, String> map = new HashMap<>(receiversCount);
            for (TransferUser txReceiver : txReceivers) {
                map.put(txReceiver.getUserData().getAddress(), txAmount);
            }
            jsonResp = fc.sendMany(sender, map);
            fee = getTransferFee(senderAddress, map);
        } else {
            // send one
            UserData receiver = txReceivers.get(0).getUserData();
            String receiverAddress = receiver.getAddress();

            jsonResp = fc.sendOne(sender, receiverAddress, txAmount);
            fee = getTransferFee(senderAddress, receiverAddress, amount);
        }

        BigDecimal senderBalance = txSender.getStartBalance();
        BigDecimal tmpSenderExpBalance = senderBalance.subtract(amountOut).subtract(fee);
        BigDecimal minAccountBalance = sender.getMinAllowedBalance();
        // update balances, if transfer is possible
        if (tmpSenderExpBalance.compareTo(minAccountBalance) >= 0) {
            Assert.assertTrue("transfer was not accepted by node", isTransferAcceptedByNode(jsonResp));

            // receivers
            TransferData txDataIn = new TransferData();
            txDataIn.setAmount(amount);
            txDataIn.setFee(BigDecimal.ZERO);
            for (TransferUser txReceiver : txReceivers) {
                txReceiver.setExpBalance(txReceiver.getStartBalance().add(amount));
                txReceiver.setTransferData(txDataIn);
            }
            //sender
            TransferData txDataOut = new TransferData();
            txDataOut.setAmount(amountOut);
            txDataOut.setFee(fee);
            txSender.setExpBalance(tmpSenderExpBalance);
            txSender.setTransferData(txDataOut);

            checkComputedFeeWithResponse(jsonResp, txDataOut);
        } else {
            log.info("Not enough funds for transfer");
            log.info("\tbalance: {}", senderBalance);
            log.info("\t amount: {}", txAmount);
            log.info("\t    fee: {}", fee);

            Assert.assertFalse("transfer was accepted by node", isTransferAcceptedByNode(jsonResp));

            for (TransferUser txReceiver : txReceivers) {
                txReceiver.setExpBalance(txReceiver.getStartBalance());
            }
            txSender.setExpBalance(senderBalance);
        }

    }

    @When("^sender sends all to receiver \\(fee is(.*)included\\)$")
    public void send_all(String included) {
        if ("not".equals(included.trim())) {
            // when fee is not included transfer will not be successful
            send_adst(txSender.getStartBalance().toString(), null);
        } else {
            UserData sender = txSender.getUserData();
            String senderAddress = sender.getAddress();
            String receiverAddress = txReceivers.get(0).getUserData().getAddress();

            // minimal account balance after transfer
            BigDecimal minAccountBalance = sender.getMinAllowedBalance();
            // subraction, because after transfer balance cannot be lesser than minimal allowed
            BigDecimal availableAmount = txSender.getStartBalance().subtract(minAccountBalance);

            // calculate approx value of maximal transfer amount
            BigDecimal feeCoefficient;
            // check if same node
            if (UserData.isAccountFromSameNode(senderAddress, receiverAddress)) {
                feeCoefficient = BigDecimal.ONE.add(EscConst.LOCAL_TX_FEE_COEFFICIENT);
            } else {
                feeCoefficient = BigDecimal.ONE.add(EscConst.LOCAL_TX_FEE_COEFFICIENT).add(EscConst.REMOTE_TX_FEE_COEFFICIENT);
            }
            BigDecimal txAmount = availableAmount.divide(feeCoefficient, 11, BigDecimal.ROUND_FLOOR);

            // increase transfer amount and check if it can be done
            BigDecimal expBalance;
            BigDecimal minAmount = new BigDecimal("0.00000000001");
            log.info("txAmount  pre: {}", txAmount);
            do {
                txAmount = txAmount.add(minAmount);
                log.info("txAmount  ins: {}", txAmount);
                expBalance = availableAmount.subtract(txAmount).subtract(getTransferFee(senderAddress, receiverAddress, txAmount));
            } while (expBalance.compareTo(BigDecimal.ZERO) >= 0);
            txAmount = txAmount.subtract(minAmount);
            log.info("txAmount post: {}", txAmount);

            send_adst(txAmount.toString(), null);
        }
    }

    private long getLastEventTimestamp(UserData receiverData) {
        FunctionCaller fc = FunctionCaller.getInstance();
        String resp = fc.getLog(receiverData);
        JsonParser parser = new JsonParser();
        JsonObject jsonResp = parser.parse(resp).getAsJsonObject();
        BigDecimal balanceRead = jsonResp.getAsJsonObject("account").get("balance").getAsBigDecimal();
        BigDecimal balanceFromLogArray = getBalanceFromLogArray(jsonResp);
        log.info("balanceFromLog      {} : receiver1", balanceRead);
        log.info("balanceFromLogArray {} : receiver1", balanceFromLogArray);
        return getLastEventTime(jsonResp);
    }

    @When("^wait for balance update")
    public void wait_for_balance_update() {
        log.info("wait for balance update :start");
        JsonParser parser = new JsonParser();
        FunctionCaller fc = FunctionCaller.getInstance();

        UserData sender = txSender.getUserData();
        long senderLastEventTs = getLastEventTimestamp(sender) + 1L;

        List<Long> receiverEventTs = new ArrayList<>(txReceivers.size());
        for (TransferUser txReceiver : txReceivers) {
            UserData receiverData = txReceiver.getUserData();
            long ts = getLastEventTimestamp(receiverData) + 1L;
            receiverEventTs.add(ts);
        }

        String resp;
        JsonObject jsonResp;
        BigDecimal balanceRead;
        BigDecimal balanceFromLogArray;

        // set of receiver index in txReceivers array that need to be checked
        Set<Integer> receiverIds = new HashSet<>(txReceivers.size());
        for (int i = 0; i < txReceivers.size(); i++) {
            receiverIds.add(i);
        }

        // max block delay for account balance update after successful remote transfer
        int MAX_BLOCK_DELAY = 50;
        int blockDelay;
        for (blockDelay = 0; blockDelay < MAX_BLOCK_DELAY; blockDelay++) {
            sleepOneBlock();
            log.info("");
            log.info("block period delay: {}", blockDelay + 1);
            log.info("");

            for (int i = 0; i < txReceivers.size(); i++) {
                if (!receiverIds.contains(i)) {
                    // current user receiver transfer
                    continue;
                }

                TransferUser txReceiver = txReceivers.get(i);
                TransferData transferData = txReceiver.getTransferData();
                /*
                Null transfer means that test is for incorrect transfer.
                In that case waiting time for balance update is extended to maximum. Early break is disabled.
                 */
                if (transferData != null) {
                    BigDecimal txAmountIn = transferData.getAmount();

                    UserData receiverData = txReceiver.getUserData();
                    long ts = receiverEventTs.get(i);
                    resp = fc.getLog(receiverData, ts);
                    jsonResp = parser.parse(resp).getAsJsonObject();

                    LogFilter lf;
                    lf = new LogFilter(true);
                    lf.addFilter("type", "send_one|send_many");
                    lf.addFilter("amount", txAmountIn.toPlainString());
                    balanceFromLogArray = getBalanceFromLogArray(jsonResp, lf);

                    if (txAmountIn.compareTo(balanceFromLogArray) == 0) {
                        // transfer received
                        receiverIds.remove(i);
                    }
                }
            }

            if (receiverIds.isEmpty()) {
                // all users received transfer
                break;
            }
        }

        if (blockDelay == MAX_BLOCK_DELAY) {
            log.info("Balance was not updated in expected time ({} block periods)", blockDelay);
        } else {
            log.info("Balance was updated in expected time ({} block periods)", blockDelay + 1);
        }


        for (int i = 0; i < txReceivers.size(); i++) {
            TransferUser txReceiver = txReceivers.get(i);
            UserData receiverData = txReceiver.getUserData();
            long ts = receiverEventTs.get(i);
            resp = fc.getLog(receiverData, ts);
            jsonResp = parser.parse(resp).getAsJsonObject();
            balanceRead = jsonResp.getAsJsonObject("account").get("balance").getAsBigDecimal();

            TransferData transferData = txReceiver.getTransferData();
            if (transferData != null) {
                BigDecimal txAmountIn = transferData.getAmount();
                LogFilter lf;
                lf = new LogFilter(false);
                lf.addFilter("type", "send_one|send_many");
                lf.addFilter("amount", txAmountIn.toPlainString());
                balanceFromLogArray = getBalanceFromLogArray(jsonResp, lf);
            } else {
                balanceFromLogArray = getBalanceFromLogArray(jsonResp);
            }

            BigDecimal receiverExpBalance = txReceiver.getExpBalance();
            log.info("balanceFromLog      {} : receiver{}", balanceRead, i);
            log.info("balanceFromLogArray {} : receiver{}", balanceFromLogArray, i);
            log.info("balanceExpected 1   {} : receiver{}", receiverExpBalance, i);
            receiverExpBalance = receiverExpBalance.add(balanceFromLogArray);
            log.info("balanceExpected 2   {} : receiver{}", receiverExpBalance, i);
            txReceiver.setExpBalance(receiverExpBalance);
        }


        resp = fc.getLog(sender, senderLastEventTs);
        jsonResp = parser.parse(resp).getAsJsonObject();
        balanceRead = jsonResp.getAsJsonObject("account").get("balance").getAsBigDecimal();
        balanceFromLogArray = getBalanceFromLogArray(jsonResp);
        BigDecimal senderExpBalance = txSender.getExpBalance();
        log.info("balanceFromLog      {} : sender", balanceRead);
        log.info("balanceFromLogArray {} : sender", balanceFromLogArray);
        log.info("balanceExpected 1   {} : sender", senderExpBalance);
        senderExpBalance = senderExpBalance.add(balanceFromLogArray);
        log.info("balanceExpected 2   {} : sender", senderExpBalance);
        txSender.setExpBalance(senderExpBalance);

        log.info("wait for balance update :end");
    }

    private void sleepOneBlock() {
        log.info("sleepOneBlock");
        try {
            Thread.sleep(100L * EscConst.BLOCK_PERIOD);
        } catch (InterruptedException e) {
            log.error("Sleep interrupted");
            log.error(e.toString());
        }
    }

    private long getLastEventTime(JsonObject jsonResp) {
        JsonArray jsonLogArray = jsonResp.getAsJsonArray("log");
        int size = jsonLogArray.size();
        long time = (size > 0) ? jsonLogArray.get(size - 1).getAsJsonObject().get("time").getAsLong() : 0;
        log.info("last log event time: {} ({})", time, Utils.formatSecondsAsDate(time));
        return time;
    }

    @Then("^receiver balance is increased by sent amount$")
    public void check_balance_chg_receiver() {
        FunctionCaller fc = FunctionCaller.getInstance();
        for (TransferUser receiver : txReceivers) {
            BigDecimal receiverExpBalance = receiver.getExpBalance();
            Assert.assertNotEquals(receiver.getStartBalance(), receiverExpBalance);
            Assert.assertEquals(receiverExpBalance, fc.getUserAccountBalance(receiver));
        }
    }

    @Then("^receiver balance does not change$")
    public void check_balance_receiver() {
        FunctionCaller fc = FunctionCaller.getInstance();
        for (TransferUser receiver : txReceivers) {
            BigDecimal receiverExpBalance = receiver.getExpBalance();
            Assert.assertEquals(receiver.getStartBalance(), receiverExpBalance);
            Assert.assertEquals(receiverExpBalance, fc.getUserAccountBalance(receiver));
        }
    }

    @Then("^sender balance is decreased by sent amount and fee$")
    public void check_balance_chg_sender() {
        BigDecimal senderExpBalance = txSender.getExpBalance();
        Assert.assertNotEquals(txSender.getStartBalance(), senderExpBalance);
        FunctionCaller fc = FunctionCaller.getInstance();
        Assert.assertEquals(senderExpBalance, fc.getUserAccountBalance(txSender));
    }

    @Then("^sender balance does not change$")
    public void check_balance_sender() {
        BigDecimal senderExpBalance = txSender.getExpBalance();
        Assert.assertEquals(txSender.getStartBalance(), senderExpBalance);
        FunctionCaller fc = FunctionCaller.getInstance();
        Assert.assertEquals(senderExpBalance, fc.getUserAccountBalance(txSender));
    }

    @Then("^sender balance is as expected$")
    public void check_balance_exp_sender() {
        FunctionCaller fc = FunctionCaller.getInstance();
        Assert.assertEquals(txSender.getExpBalance(), fc.getUserAccountBalance(txSender));
    }

    @Then("^receiver balance is as expected$")
    public void check_balance_exp_receiver() {
        FunctionCaller fc = FunctionCaller.getInstance();
        for (TransferUser receiver : txReceivers) {
            Assert.assertEquals(receiver.getExpBalance(), fc.getUserAccountBalance(receiver));
        }
    }

    @Given("user log")
    public void user_log() {
        List<UserData> userDataList = UserDataProvider.getInstance().getUserDataList();

        for (UserData user : userDataList) {
            FunctionCaller fc = FunctionCaller.getInstance();
            String userLog = fc.getLog(user);
//            fc.jsonPrettyPrint(userLog);
//
//            byte[] encoded;
//            String userLog;
//            try {
//                encoded = Files.readAllBytes(Paths.get("file.json"));
//                userLog = new String(encoded, StandardCharsets.UTF_8);
//            } catch (IOException e) {
//                e.printStackTrace();
//                userLog ="";
//            }

            log.debug(user.getAddress());
            checkUserLog(userLog);
            log.debug("success");
        }
    }

    /**
     * Checks user log returned by get_log function.
     *
     * @param userLog get_log response
     */
    private void checkUserLog(String userLog) {
        JsonParser parser = new JsonParser();
        JsonObject jsonResp = parser.parse(userLog).getAsJsonObject();

        // balance read from response
        BigDecimal balanceRead = new BigDecimal(jsonResp.getAsJsonObject("account").get("balance").getAsString());
        // balance computed from all operations in user log
        BigDecimal balance = getBalanceFromLogArray(jsonResp);

        Assert.assertEquals(balance, balanceRead);
    }

    /**
     * Sums all operations in log
     *
     * @param jsonResp json returned from get_log function
     * @return balance computed from operations in user log array
     */
    private BigDecimal getBalanceFromLogArray(JsonObject jsonResp) {
        return getBalanceFromLogArray(jsonResp, null);
    }

    /**
     * Sums log operations that match filter
     *
     * @param jsonResp json returned from get_log function
     * @param filter   LogFilter, null for all operations
     * @return balance computed from filtered operations in user log array
     */
    private BigDecimal getBalanceFromLogArray(JsonObject jsonResp, LogFilter filter) {
        BigDecimal balance = BigDecimal.ZERO;

        // node
        int node = jsonResp.getAsJsonObject("account").get("node").getAsInt();
        log.info("NODE {}", node);

        JsonElement jsonElementLog = jsonResp.get("log");
        if (jsonElementLog.isJsonArray()) {
            JsonArray jsonArrayLog = jsonElementLog.getAsJsonArray();
            for (JsonElement je : jsonArrayLog) {
                JsonObject logEntry = je.getAsJsonObject();
                // text log entry type
                String type = logEntry.get("type").getAsString();
                // numeric log entry type
                String typeNo = logEntry.get("type_no").getAsString();


                // checking entry with filter
                if (filter != null) {
                    if (!filter.processEntry(logEntry)) {
                        log.info("skipping: {}", type);
                        continue;
                    }
                }


                BigDecimal amount;
                if ("create_account".equals(type) || "change_account_key".equals(type)
                        || "send_one".equals(type) || "send_many".equals(type)
                        || ("create_node".equals(type) && "7".equals(typeNo))) {// create_node request
                    amount = new BigDecimal(logEntry.get("amount").getAsString());
                    if ("out".equals(logEntry.get("inout").getAsString())) {
                        BigDecimal senderFee = new BigDecimal(logEntry.get("sender_fee").getAsString());
                        amount = amount.subtract(senderFee);
                    }

                } else if ("dividend".equals(type)) {
                    amount = new BigDecimal(logEntry.get("dividend").getAsString());

                } else if ("node_started".equals(type)) {// type_no == 32768
                    amount = new BigDecimal(logEntry.getAsJsonObject("account").get("balance").getAsString());
                    if (logEntry.has("dividend")) {
                        BigDecimal dividend = new BigDecimal(logEntry.get("dividend").getAsString());
                        amount = amount.add(dividend);
                    }

                } else if ("bank_profit".equals(type)) {// type_no == 32785
                    if (logEntry.has("node") && logEntry.get("node").getAsInt() != node) {
                        log.info("bank profit for different node");
                        amount = BigDecimal.ZERO;
                    } else {
                        amount = new BigDecimal(logEntry.get("profit").getAsString());
                        if (logEntry.has("fee")) {
                            BigDecimal fee = new BigDecimal(logEntry.get("fee").getAsString());
                            amount = amount.subtract(fee);
                        }
                    }

                } else if ("create_node".equals(type) && "32775".equals(typeNo)) {
                    // create_node request accepted
                    amount = BigDecimal.ZERO;

                } else {
                    log.warn("Unknown type: " + type + ", no " + typeNo);
                    amount = BigDecimal.ZERO;

                }
                balance = balance.add(amount);
                log.info(String.format("%1$20s:%2$s", type, amount.toString()));
            }
        }

        return balance;
    }

    /**
     * Returns transfer fee in tokens
     *
     * @param senderAddress   sender address
     * @param receiverAddress receiver address
     * @param amount          transfer amount
     * @return transfer fee in tokens
     */
    private BigDecimal getTransferFee(String senderAddress, String receiverAddress, BigDecimal amount) {
        Map<String, String> receiverMap = new HashMap<>(1);
        receiverMap.put(receiverAddress, amount.toPlainString());
        return getTransferFee(senderAddress, receiverMap);
    }

    /**
     * Returns transfer fee in tokens
     *
     * @param senderAddress sender address
     * @param receiverMap   map of receiver - amount pairs
     * @return transfer fee in tokens
     */
    private BigDecimal getTransferFee(String senderAddress, Map<String, String> receiverMap) {
        BigDecimal summaryFee = BigDecimal.ZERO;

        for (String receiverAddress : receiverMap.keySet()) {
            BigDecimal amount = new BigDecimal(receiverMap.get(receiverAddress));

            BigDecimal localFee = amount.multiply(EscConst.LOCAL_TX_FEE_COEFFICIENT);
            // fee scale must be set, because multiply extends scale
            localFee = localFee.setScale(11, BigDecimal.ROUND_FLOOR);
            summaryFee = summaryFee.add(localFee);

            if (!UserData.isAccountFromSameNode(senderAddress, receiverAddress)) {
                // users in different nodes
                BigDecimal remoteFee = amount.multiply(EscConst.REMOTE_TX_FEE_COEFFICIENT);
                // fee scale must be set, because multiply extends scale
                remoteFee = remoteFee.setScale(11, BigDecimal.ROUND_FLOOR);
                summaryFee = summaryFee.add(remoteFee);
            }
        }

        return EscConst.MIN_TX_FEE.max(summaryFee);
    }

    /**
     * Compares transfer amount and computed fee with values returned from sendOne (tx/deduct, tx/fee) function.
     *
     * @param jsonResp     send_one response as String
     * @param transferData transfer data
     */
    private void checkComputedFeeWithResponse(String jsonResp, TransferData transferData) {
        JsonParser parser = new JsonParser();
        JsonObject o = parser.parse(jsonResp).getAsJsonObject();
        o = o.getAsJsonObject("tx");

        BigDecimal amount = o.get("deduct").getAsBigDecimal();
        BigDecimal fee = o.get("fee").getAsBigDecimal();

        Assert.assertEquals("Invalid transfer amount.", amount, transferData.getAmount());
        Assert.assertEquals("Invalid transfer fee.", fee, transferData.getFee());
    }

    /**
     * Checks, if node accepted transfer.
     *
     * @param jsonResp response from transfer function (eg. send_one, send_many) as String
     * @return true, if transfer was accepted by node, false otherwise
     */
    private boolean isTransferAcceptedByNode(String jsonResp) {
        JsonParser parser = new JsonParser();
        JsonObject o = parser.parse(jsonResp).getAsJsonObject();
        o = o.getAsJsonObject("tx");

        return o.has("id");
    }
}