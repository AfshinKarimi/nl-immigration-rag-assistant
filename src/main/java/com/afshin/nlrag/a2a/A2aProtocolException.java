package com.afshin.nlrag.a2a;

/**
 * JSON-RPC application error. HTTP status stays 200; the body carries
 * {@code error.code} as A2A/JSON-RPC require.
 */
public class A2aProtocolException extends RuntimeException {

    private final int code;

    public A2aProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
