package com.context_platform.aether.integration.security;

import javax.crypto.SecretKey;

public interface KeyProvider {
    SecretKey getKey(String keyVersion);
    String getDefaultKeyVersion();
}

