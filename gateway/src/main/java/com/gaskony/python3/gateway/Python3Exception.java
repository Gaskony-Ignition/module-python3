package com.gaskony.python3.gateway;

/**
 * Exception thrown when Python 3 execution fails
 */
public class Python3Exception extends Exception {

    private final String traceback;

    public Python3Exception(String message) {
        super(message);
        this.traceback = null;
    }

    public Python3Exception(String message, Throwable cause) {
        super(message, cause);
        this.traceback = null;
    }

    public Python3Exception(String message, String traceback) {
        super(message + (traceback != null ? "\n" + traceback : ""));
        this.traceback = traceback;
    }

    public String getTraceback() {
        return traceback;
    }

    public boolean hasTraceback() {
        return traceback != null;
    }
}
