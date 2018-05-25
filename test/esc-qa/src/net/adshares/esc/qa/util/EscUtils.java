package net.adshares.esc.qa.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class EscUtils {
    /**
     * Checks, if node accepted transaction.
     *
     * @param jsonResp response from function (eg. send_one, send_many) as String
     * @return true, if transfer was accepted by node, false otherwise
     */
    public static boolean isTransactionAcceptedByNode(String jsonResp) {
        JsonParser parser = new JsonParser();
        JsonObject o = parser.parse(jsonResp).getAsJsonObject();
        o = o.getAsJsonObject("tx");

        return o.has("id");
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
