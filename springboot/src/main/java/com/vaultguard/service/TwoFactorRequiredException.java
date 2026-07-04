package com.vaultguard.service;

import java.util.List;
import java.util.Map;

public class TwoFactorRequiredException extends RuntimeException {
    private final String userUuid;
    private final List<Integer> providers;
    private final Map<String, Object> providerMetadata;

    public TwoFactorRequiredException(String userUuid, List<Integer> providers,
                                      Map<String, Object> providerMetadata) {
        super("Two factor required.");
        this.userUuid = userUuid;
        this.providers = providers;
        this.providerMetadata = providerMetadata;
    }

    public String getUserUuid() { return userUuid; }
    public List<Integer> getProviders() { return providers; }
    public Map<String, Object> getProviderMetadata() { return providerMetadata; }
}
