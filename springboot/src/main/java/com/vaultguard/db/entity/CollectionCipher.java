package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "ciphers_collections")
@IdClass(CollectionCipher.CollectionCipherId.class)
public class CollectionCipher {

    @Id
    @Column(name = "collection_uuid", length = 36)
    private String collectionUuid;

    @Id
    @Column(name = "cipher_uuid", length = 36)
    private String cipherUuid;

    public static class CollectionCipherId implements Serializable {
        private String collectionUuid;
        private String cipherUuid;

        public CollectionCipherId() {}

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollectionCipherId other)) return false;
            return Objects.equals(collectionUuid, other.collectionUuid) &&
                   Objects.equals(cipherUuid, other.cipherUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectionUuid, cipherUuid);
        }
    }

    public String getCollectionUuid() { return collectionUuid; }
    public void setCollectionUuid(String collectionUuid) { this.collectionUuid = collectionUuid; }
    public String getCipherUuid() { return cipherUuid; }
    public void setCipherUuid(String cipherUuid) { this.cipherUuid = cipherUuid; }
}
