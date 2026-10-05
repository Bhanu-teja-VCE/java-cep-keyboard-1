package com.switchtype.exception;

/** A settings value is outside its allowed range. */
public class InvalidSettingsException extends SwitchTypeException {

    private static final long serialVersionUID = 1L;

    public InvalidSettingsException(String message) {
        super(message);
    }
}
