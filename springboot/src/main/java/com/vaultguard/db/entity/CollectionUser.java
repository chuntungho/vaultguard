package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "users_collections")
@IdClass(CollectionUser.CollectionUserId.class)
public class CollectionUser {

    @Id
    @Column(name = "collection_uuid", length = 36)
    private String collectionUuid;

    @Id
    @Column(name = "users_organizations_uuid", length = 36)
    private String orgMembershipUuid;

    @Column(name = "read_only", nullable = false)
    private boolean readOnly = false;

    @Column(name = "hide_passwords", nullable = false)
    private boolean hidePasswords = false;

    public static class CollectionUserId implements Serializable {
        private String collectionUuid;
        private String orgMembershipUuid;

        public CollectionUserId() {}

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CollectionUserId other)) return false;
            return Objects.equals(collectionUuid, other.collectionUuid) &&
                   Objects.equals(orgMembershipUuid, other.orgMembershipUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(collectionUuid, orgMembershipUuid);
        }
    }

    public String getCollectionUuid() { return collectionUuid; }
    public void setCollectionUuid(String collectionUuid) { this.collectionUuid = collectionUuid; }
    public String getOrgMembershipUuid() { return orgMembershipUuid; }
    public void setOrgMembershipUuid(String orgMembershipUuid) { this.orgMembershipUuid = orgMembershipUuid; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public boolean isHidePasswords() { return hidePasswords; }
    public void setHidePasswords(boolean hidePasswords) { this.hidePasswords = hidePasswords; }
}
