package com.switchtype.exception;

/** Profiles, sessions or the language model could not be loaded or saved (file or database). */
public class ProfileStoreException extends SwitchTypeException {

    private static final long serialVersionUID = 1L;

    public ProfileStoreException(String message) {
        super(message);
    }

    public ProfileStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
