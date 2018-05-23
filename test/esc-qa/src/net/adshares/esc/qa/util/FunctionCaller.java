package net.adshares.esc.qa.util;

import com.google.gson.*;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.stepdefs.TransferUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class FunctionCaller {

    private static final String ESC_BINARY = "docker exec -i -w /tmp/esc adshares_esc_1 esc";
    private static final String ESC_BINARY_OPTS = " -n0 ";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static FunctionCaller instance;

    private FunctionCaller() {
    }

    public static FunctionCaller getInstance() {
        if (instance == null) {
            instance = new FunctionCaller();
        }
        return instance;
    }

    /**
     * Calls broadcast function, which sends message to the network.
     *
     * @param userData user data
     * @param message  message as hexadecimal String with even number of characters
     * @return response: json when request was correct, empty otherwise
     */
    public String broadcast(UserData userData, String message) {
        log.info("broadcast");
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"broadcast\", \"message\":\"%s\"}') | ", message)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        String output = callFunction(command);
        output = output.replaceFirst(".*}\\s*\\{", "{");
        return output;
    }

    /**
     * Calls get_broadcast function, which gets broadcast messages from last block.
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    public String getBroadcast(UserData userData) {
        return getBroadcast(userData, "0");
    }

    /**
     * Calls get_broadcast function, which gets broadcast messages from block.
     *
     * @param userData  user data
     * @param blockTime block time in Unix Epoch seconds, 0 for last block
     * @return response: json when request was correct, empty otherwise
     */
    public String getBroadcast(UserData userData, String blockTime) {
        log.info("getBroadcast");
        String command = String.format("echo '{\"run\":\"get_broadcast\", \"from\":\"%s\"}' | ", blockTime)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        return callFunction(command);
    }

    /**
     * Calls get_me function for specific user.
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    private String getMe(UserData userData) {
        log.info("getMe");
        String command = ("echo '{\"run\":\"get_me\"}' | ")
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        return callFunction(command);
    }

    /**
     * Calls get_log function for specific user.
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    public String getLog(UserData userData) {
        return getLog(userData, 0L);
    }

    /**
     * Calls get_log function for specific user.
     *
     * @param userData      user data
     * @param fromTimeStamp log start time in Unix Epoch seconds
     * @return response: json when request was correct, empty otherwise
     */
    public String getLog(UserData userData, long fromTimeStamp) {
        log.info("getLog from {}", fromTimeStamp);
        String command = String.format("echo '{\"run\":\"get_log\", \"from\":\"%d\"}' | ", fromTimeStamp)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        return callFunction(command);
    }

    /**
     * Calls retrieve_funds function.
     *
     * @param userData      data of user, who will retrieve funds
     * @param remoteAddress account address from which funds will be retrieved
     * @return response: json when request was correct, empty otherwise
     */
    public String retrieveFunds(UserData userData, String remoteAddress) {
        log.info("retrieveFunds by {} from {}", userData.getAddress(), remoteAddress);
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"retrieve_funds\", \"address\":\"%s\"}') | ", remoteAddress)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        String output = callFunction(command);
        output = output.replaceFirst(".*}\\s*\\{", "{");
        return output;
    }

    /**
     * Calls send_one function
     *
     * @param sender          sender data
     * @param receiverAddress receiver address
     * @param amount          transfer amount
     * @return response: json when request was correct, empty otherwise
     */
    public String sendOne(UserData sender, String receiverAddress, String amount) {
        log.info("sendOne {}->{}: {}", sender.getAddress(), receiverAddress, amount);
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"send_one\", \"address\":\"%s\", \"amount\":\"%s\"}') | ", receiverAddress, amount)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(sender.getDataAsEscParams());
        String output = callFunction(command);
        output = output.replaceFirst(".*}\\s*\\{", "{");
        return output;
    }

    /**
     * Calls send_many function
     *
     * @param sender      sender data
     * @param receiverMap map of receiver - amount pairs
     * @return response: json when request was correct, empty otherwise
     */
    public String sendMany(UserData sender, Map<String, String> receiverMap) {
        log.info("sendMany {}->", sender.getAddress());
        for (Map.Entry<String, String> entry : receiverMap.entrySet()) {
            log.info("sendMany ->{}: {}", entry.getKey(), entry.getValue());
        }
        Gson gson = new GsonBuilder().create();
        String wires = gson.toJson(receiverMap);
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"send_many\", \"wires\":%s}') | ", wires)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(sender.getDataAsEscParams());
        String output = callFunction(command);
        output = output.replaceFirst(".*}\\s*\\{", "{");
        return output;
    }

    /**
     * Calls command in sh shell
     *
     * @param cmd command
     * @return stdout response
     */
    public String callFunction(String cmd) {
        log.debug("request: {}", cmd);
        String[] cmdArr = {
                "/bin/sh",
                "-c",
                cmd
        };

        StringBuilder sb = new StringBuilder();
        Process proc;
        try {
            proc = Runtime.getRuntime().exec(cmdArr);

            String line;
            BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            in.close();
            proc.waitFor();

        } catch (IOException e) {
            log.error("Cannot read from ESC");
        } catch (InterruptedException e) {
            log.error("ESC process timeout");
        }
        String resp = sb.toString();
        log.debug("resp: {}", resp);
        if ("".equals(resp)) {
            log.warn("Empty response for: {}", cmd);
        }
        return resp;
    }

    /**
     * Returns timestamp of last event in log.
     *
     * @param userData user data
     * @return timestamp of last event in log or 0 if log is empty
     */
    public long getLastEventTimestamp(UserData userData) {
        JsonParser parser = new JsonParser();
        JsonObject jsonResp = parser.parse(getLog(userData)).getAsJsonObject();
        JsonArray jsonLogArray = jsonResp.getAsJsonArray("log");
        int size = jsonLogArray.size();
        long time = (size > 0) ? jsonLogArray.get(size - 1).getAsJsonObject().get("time").getAsLong() : 0;
        log.info("last log event time: {} ({})", time, Utils.formatSecondsAsDate(time));
        return time;
    }

    /**
     * Returns user account balance. Shortcut for getting balance from get_me response
     *
     * @param userData user data
     * @return user account balance
     */
    public BigDecimal getUserAccountBalance(UserData userData) {
        JsonParser parser = new JsonParser();
        JsonObject o = parser.parse(getMe(userData)).getAsJsonObject();
        String balance = o.getAsJsonObject("account").get("balance").getAsString();
        log.info("user {} balance: {}", userData.getAddress(), balance);
        return new BigDecimal(balance);
    }

    /**
     * Returns user account balance. Shortcut for getting balance from get_me response
     *
     * @param user user
     * @return user account balance
     */
    public BigDecimal getUserAccountBalance(TransferUser user) {
        return getUserAccountBalance(user.getUserData());
    }

}
