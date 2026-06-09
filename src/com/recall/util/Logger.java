package com.recall.util;

import com.recall.Config;

/**
 * Logger - Simple logging utility for debug output
 *
 * Purpose: Centralized logging with levels (INFO, WARN, ERROR, DEBUG).
 * Much better than scattered System.out.println() statements.
 */
public class Logger {
    private static final String LOG_TAG = "[ReCall]";

    public static void info(String message) {
        System.out.println(LOG_TAG + " INFO: " + message);
    }

    public static void warn(String message) {
        System.out.println(LOG_TAG + " WARN: " + message);
    }

    public static void error(String message) {
        System.err.println(LOG_TAG + " ERROR: " + message);
    }

    public static void debug(String message) {
        if(Config.DEBUG_MODE) {
            System.out.println(LOG_TAG + " DEBUG: " + message);
        }
    }
}
