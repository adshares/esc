package net.adshares.esc.qa.util;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Used for selecting/excluding events from log (get_log response)
 */
public class LogFilter {
    /**
     * filter type: true - require, false - exclude
     */
    private boolean isRequired;
    /**
     * filter criterions:<br />
     * - key is Json object field name, <br />
     * - value is value of that field.
     */
    private Map<String, String> filterMap;

    /**
     * @param isRequired filter type: true - require, false - exclude
     */
    public LogFilter(boolean isRequired) {
        this.isRequired = isRequired;
        this.filterMap = new HashMap<>();
    }

    /**
     * Adds filter criterion.
     *
     * @param key   Json object field name
     * @param value value of Json field
     */
    public void addFilter(String key, String value) {
        filterMap.put(key, value);
    }

    /**
     * Matches log entry with criterions.
     *
     * @param o log entry
     * @return true if entry should be processed, false if entry should be skipped
     */
    public boolean processEntry(JsonObject o) {
        boolean isMatch = false;

        if (o != null) {
            for (String key : filterMap.keySet()) {
                if (o.has(key)) {
                    isMatch = o.get(key).getAsString().matches(filterMap.get(key));
                } else {
                    isMatch = false;
                }
                if (!isMatch) {
                    break;
                }
            }
        }

        return (isMatch && isRequired) || (!isMatch && !isRequired);
    }

}
