package net.adshares.esc.qa.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class EscUtils {
    /**
     * Checks, if node accepted transaction.
     *
     * @param jsonResp response from function (eg. send_one, send_many) as String
     * @return true, if transfer was accepted by node, false otherwise
     */
    public static boolean isTransactionAcceptedByNode(String jsonResp) {
        JsonObject o = Utils.convertStringToJsonObject(jsonResp);

        if (o.has("error")) {
            return false;
        }

        o = o.getAsJsonObject("tx");
        return o.has("id");
    }

    /**
     * Returns error description or empty String "", if no error is present.
     *
     * @param jsonResp response from function (eg. send_one, send_many) as String
     * @return error description or empty String "", if no error is present
     */
    public static String getErrorDescription(String jsonResp) {
        JsonObject o = Utils.convertStringToJsonObject(jsonResp);
        return o.has("error") ? o.get("error").getAsString() : "";
    }

    /**
     * Returns timestamp of last event in log.
     *
     * @param jsonObject json response for get_log function
     * @return timestamp of last event in log or 0, if log is empty
     */
    static LogEventTimestamp getLastLogEventTimestamp(JsonObject jsonObject) {
        long timeStamp = 0;
        int eventsCount = 0;
        if (jsonObject.has("log")) {
            JsonElement jsonElementLog = jsonObject.get("log");
            if (jsonElementLog.isJsonArray()) {
                JsonArray arr = jsonElementLog.getAsJsonArray();

                int size = arr.size();
                if (size > 0) {
                    int index = size - 1;
                    JsonObject entry = arr.get(index).getAsJsonObject();
                    timeStamp = entry.get("time").getAsLong();
                    ++eventsCount;

                    while (index > 0) {
                        --index;

                        if (timeStamp != arr.get(index).getAsJsonObject().get("time").getAsLong()) {
                            break;
                        }
                        ++eventsCount;
                    }
                }
            }
        }
        return new LogEventTimestamp(timeStamp, eventsCount);
    }

    /**
     * Returns hex String for next block.
     *
     * @param blockTime hex String
     * @return next block time in hex
     */
    public static String getNextBlock(String blockTime) {
        int time = Integer.parseInt(blockTime, 16);
        time += EscConst.BLOCK_PERIOD;
        return Integer.toHexString(time);
    }
}
