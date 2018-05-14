package net.adshares.esc.qa.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.stepdefs.TransferUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.Map;

public class FunctionCaller {

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
     * Calls get_me function for specific user
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    public String getMe(UserData userData) {
        log.info("getMe");
        String command = "echo '{\"run\":\"get_me\"}' | ./esc -n0 ".concat(userData.getDataAsEscParams());
        return callFunction(command);
    }

    /**
     * Calls get_log function for specific user
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    public String getLog(UserData userData) {
        return getLog(userData, 0L);
    }

    /**
     * Calls get_log function for specific user
     *
     * @param userData      user data
     * @param fromTimeStamp log start time in Unix Epoch seconds
     * @return response: json when request was correct, empty otherwise
     */
    public String getLog(UserData userData, long fromTimeStamp) {
        log.info("getLog from {}", fromTimeStamp);
        String command = String.format("echo '{\"run\":\"get_log\", \"from\":\"%d\"}' | ./esc -n0 ", fromTimeStamp);
        command = command.concat(userData.getDataAsEscParams());
        return callFunction(command);
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
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"send_one\", \"address\":\"%s\", \"amount\":\"%s\"}') | ./esc -n0 ", receiverAddress, amount).concat(sender.getDataAsEscParams());
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
        String command = String.format("(echo '{\"run\":\"get_me\"}';echo '{\"run\":\"send_many\", \"wires\":%s}') | ./esc -n0 ", wires).concat(sender.getDataAsEscParams());
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
    private String callFunction(String cmd) {
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
