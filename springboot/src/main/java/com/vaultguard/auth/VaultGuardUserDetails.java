package com.vaultguard.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class VaultGuardUserDetails implements UserDetails {

    private final String userUuid;
    private final String deviceUuid;

    public VaultGuardUserDetails(String userUuid, String deviceUuid) {
        this.userUuid = userUuid;
        this.deviceUuid = deviceUuid;
    }

    public String getUserUuid() { return userUuid; }
    public String getDeviceUuid() { return deviceUuid; }

    @Override public String getUsername() { return userUuid; }
    @Override public String getPassword() { return null; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
