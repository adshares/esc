package net.adshares.esc.qa.util;

import com.google.gson.*;
import net.adshares.esc.qa.data.UserData;
import net.adshares.esc.qa.stepdefs.TransferUser;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.util.Iterator;
import java.util.Map;

public class FunctionCaller {

    private static final String ESC_BINARY = "docker exec -i -w /tmp/esc adshares_esc_1 esc";
    private static final String ESC_BINARY_OPTS = " -n0 ";
    /**
     * Name of temporary file that is created for commands that cannot be called directly in shell
     */
    private static final String TEMP_FILE_NAME = "tmp";

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
     * Calls get_block function.
     *
     * @param userData user data
     * @return response: json when request was correct, empty otherwise
     */
    public String getBlock(UserData userData) {
        log.info("getBlock");
        String command = ("echo '{\"run\":\"get_block\"}' | ")
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        return callFunction(command);
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
     * Calls get_log function for specific user.
     *
     * @param userData      user data
     * @param logEventTimeStamp log start timestamp
     * @return response: json when request was correct, empty otherwise
     */
    public String getLog(UserData userData, LogEventTimestamp logEventTimeStamp) {
        log.info("getLog from {}", logEventTimeStamp);
        long timestamp = logEventTimeStamp.getTimestamp();
        String command = String.format("echo '{\"run\":\"get_log\", \"from\":\"%d\"}' | ", timestamp)
                .concat(ESC_BINARY).concat(ESC_BINARY_OPTS).concat(userData.getDataAsEscParams());
        String resp = callFunction(command);

        // if eventNum is lesser than 2, no event will be removed
        int eventNum = logEventTimeStamp.getEventNum();
        if (eventNum > 1) {
            JsonObject o = Utils.convertStringToJsonObject(resp);
            JsonElement jsonElementLog = o.get("log");
            if (jsonElementLog.isJsonArray()) {
                JsonArray arr = jsonElementLog.getAsJsonArray();
                if (arr.size() > 0) {
                    Iterator<JsonElement> it = arr.iterator();

                    int eventCount = 0;
                    while(it.hasNext()) {
                        JsonObject entry = it.next().getAsJsonObject();
                        // events in log are sorted by time:
                        // if event time is different than timestamp,
                        // this means that event happened later than timestamp
                        long ts = entry.get("time").getAsLong();
                        if (ts != timestamp) {
                            break;
                        }
                        ++eventCount;
                        if (eventCount >= eventNum) {
                            break;
                        }
                        it.remove();
                        log.error("REMOVED");
                    }

                    Gson gson = new GsonBuilder().create();
                    resp = gson.toJson(o);

                }
            }
        }

        return resp;
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

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);

        CommandLine cmdLine = new CommandLine("/bin/sh");

        if (cmd.length() < 12000) {
            cmdLine.addArgument("-c").addArgument(cmd, false);
        } else {
            log.info("command length {}", cmd.length());
            // when command is too long it is not possible to call it using sh shell
            // command is saved to file and file is new command

            PrintWriter writer;
            try {
                writer = new PrintWriter(TEMP_FILE_NAME, "UTF-8");
                writer.println(cmd);
                writer.close();
            } catch (FileNotFoundException e) {
                log.error("File not found");
                log.error(e.toString());
            } catch (UnsupportedEncodingException uee) {
                log.error("Unsupported Encoding");
                log.error(uee.toString());
            }
            cmdLine.addArgument(TEMP_FILE_NAME);
        }

        DefaultExecutor executor = new DefaultExecutor();
        executor.setStreamHandler(streamHandler);
        executor.setWatchdog(new ExecuteWatchdog(60000L));// 60,000 ms = 1 min.
        try {
            executor.execute(cmdLine);
        } catch (IOException e) {
            log.error("Cannot read from ESC");
            log.error(e.toString());
        }
        String resp = outputStream.toString();
        log.debug("resp: {}", resp);
        if ("".equals(resp)) {
            log.warn("Empty response for: {}", cmd);
        }

        try {
            Utils.deleteDirectory(TEMP_FILE_NAME);
        } catch (IOException e) {
            log.error("Cannot delete {}", TEMP_FILE_NAME);
            log.error(e.toString());
        }

        return resp;
    }

    /**
     * Returns timestamp of last event in log.
     *
     * @param userData user data
     * @return timestamp of last event in log or 0, if log is empty
     */
    public LogEventTimestamp getLastEventTimestamp(UserData userData) {
        JsonObject o = Utils.convertStringToJsonObject(getLog(userData));

        LogEventTimestamp let = EscUtils.getLastLogEventTimestamp(o);
        log.info("last log event time: {} ({}) for {}", let, Utils.formatSecondsAsDate(let.getTimestamp()),
                o.getAsJsonObject("account").get("address").getAsString());
        return let;
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
