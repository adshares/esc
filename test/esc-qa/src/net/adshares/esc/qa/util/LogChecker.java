package net.adshares.esc.qa.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class LogChecker {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private JsonObject jsonResp;

    public LogChecker() {
    }

    public LogChecker(String resp) {
        JsonParser parser = new JsonParser();
        this.jsonResp = convertStringToJsonObject(resp);
    }

    public void setResp(String resp) {
        this.jsonResp = convertStringToJsonObject(resp);
    }

    private JsonObject convertStringToJsonObject(String resp) {
        JsonParser parser = new JsonParser();
        return parser.parse(resp).getAsJsonObject();
    }

    /**
     * @return balance from account object (account.balance)
     */
    public BigDecimal getBalanceFromAccountObject() {
        return new BigDecimal(jsonResp.getAsJsonObject("account").get("balance").getAsString());
    }

    /**
     * Sums all operations in log
     *
     * @return balance computed from operations in user log array
     */
    public BigDecimal getBalanceFromLogArray() {
        return getBalanceFromLogArray(null);
    }

    /**
     * Sums log operations that match filter
     *
     * @param filter LogFilter, null for all operations
     * @return balance computed from filtered operations in user log array
     */
    public BigDecimal getBalanceFromLogArray(LogFilter filter) {
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
}
