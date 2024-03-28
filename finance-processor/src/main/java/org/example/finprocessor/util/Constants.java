package org.example.finprocessor.util;

public class Constants {
    private Constants() {
    }

    public static final String REMOTE_HOST_FORMAT = "http://%s:%d";

    public static final String KSTREAM_PREFIX = "kstream-";

    public static final String TOP_PREDICTIONS_STORE = "top-predictions-state-store";
    public static final String PREDICTIONS_STORE = "predictions-state-store";
    public static final String PREDICTIONS_TIME_WINDOWED_STORE = "predictions-time-windowed-state-store";
    public static final String PREDICTIONS_SESSION_WINDOWED_STORE = "predictions-session-windowed-state-store";
    public static final String TOP_PREDICTIONS = "top-predictions";
    public static final String LOSS_PREDICTIONS_STORE = "loss-predictions-state-store";
}
