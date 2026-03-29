package com.vaultguard.service;

public class TwoFactorRequiredException extends RuntimeException {
    private final String userUuid;

    public TwoFactorRequiredException(String userUuid) {
        super("Two-factor authentication required");
        this.userUuid = userUuid;
    }

    public String getUserUuid() { return userUuid; }
}
