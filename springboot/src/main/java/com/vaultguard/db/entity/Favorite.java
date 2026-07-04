package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "favorites")
@IdClass(Favorite.FavoriteId.class)
public class Favorite {

    @Id
    @Column(name = "user_uuid", length = 36)
    private String userUuid;

    @Id
    @Column(name = "cipher_uuid", length = 36)
    private String cipherUuid;

    public Favorite() {}

    public Favorite(String userUuid, String cipherUuid) {
        this.userUuid = userUuid;
        this.cipherUuid = cipherUuid;
    }

    public static class FavoriteId implements Serializable {
        private String userUuid;
        private String cipherUuid;

        public FavoriteId() {}
        public FavoriteId(String userUuid, String cipherUuid) {
            this.userUuid = userUuid;
            this.cipherUuid = cipherUuid;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FavoriteId other)) return false;
            return Objects.equals(userUuid, other.userUuid) && Objects.equals(cipherUuid, other.cipherUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userUuid, cipherUuid);
        }
    }

    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getCipherUuid() { return cipherUuid; }
    public void setCipherUuid(String cipherUuid) { this.cipherUuid = cipherUuid; }
}
