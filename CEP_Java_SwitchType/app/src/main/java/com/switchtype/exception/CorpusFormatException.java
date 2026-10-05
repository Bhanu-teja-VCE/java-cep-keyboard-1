package com.switchtype.exception;

/** A corpus or vocabulary file cannot be used (missing, empty, binary or unreadable). */
public class CorpusFormatException extends SwitchTypeException {

    private static final long serialVersionUID = 1L;

    public CorpusFormatException(String message) {
        super(message);
    }

    public CorpusFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
