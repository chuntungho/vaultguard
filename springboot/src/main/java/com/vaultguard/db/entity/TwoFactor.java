package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "twofactor")
@IdClass(TwoFactor.TwoFactorId.class)
public class TwoFactor {

    @Id
    @Column(name = "user_uuid", length = 36)
    private String userUuid;

    @Id
    @Column(name = "type", nullable = false)
    private int type;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(name = "last_used")
    private Instant lastUsed;

    public static class TwoFactorId implements Serializable {
        private String userUuid;
        private int type;

        public TwoFactorId() {}
        public TwoFactorId(String userUuid, int type) {
            this.userUuid = userUuid;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TwoFactorId other)) return false;
            return type == other.type && Objects.equals(userUuid, other.userUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userUuid, type);
        }
    }

    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
}
