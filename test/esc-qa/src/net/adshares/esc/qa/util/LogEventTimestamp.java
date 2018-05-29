package net.adshares.esc.qa.util;

/**
 * Extended timestamp for get_log section.
 * Timestamp consist of time in seconds and event number in particular second.
 */
public class LogEventTimestamp {
    private long timestamp;
    private int eventNum;

    LogEventTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.eventNum = 0;
    }

    LogEventTimestamp(long timestamp, int eventNum) {
        this.timestamp = timestamp;
        this.eventNum = eventNum;
    }

    long getTimestamp() {
        return timestamp;
    }

    int getEventNum() {
        return eventNum;
    }

    /**
     * By incrementing event number, timestamp moves to next event
     */
    public LogEventTimestamp incrementEventNum() {
        ++eventNum;
        return this;
    }

    @Override
    public String toString() {
        return String.format("%d_%d", timestamp, eventNum);
    }
}
