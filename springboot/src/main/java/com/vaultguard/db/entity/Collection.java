package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "collections")
public class Collection {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "org_uuid", nullable = false, length = 36)
    private String orgUuid;

    @Column(name = "name", nullable = false, columnDefinition = "TEXT")
    private String name;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getOrgUuid() { return orgUuid; }
    public void setOrgUuid(String orgUuid) { this.orgUuid = orgUuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
