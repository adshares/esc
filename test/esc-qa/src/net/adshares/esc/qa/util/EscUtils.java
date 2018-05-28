package net.adshares.esc.qa.util;

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
