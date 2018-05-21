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
}
