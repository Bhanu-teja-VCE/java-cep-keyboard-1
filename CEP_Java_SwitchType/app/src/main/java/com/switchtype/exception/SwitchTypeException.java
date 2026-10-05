package com.switchtype.exception;

/**
 * Base class of SwitchType's own checked exceptions (Unit II: user-defined exceptions).
 * Callers can catch this one type when they do not care which part failed.
 */
public class SwitchTypeException extends Exception {

    private static final long serialVersionUID = 1L;

    public SwitchTypeException(String message) {
        super(message);
    }

    public SwitchTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}
