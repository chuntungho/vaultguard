package com.vaultguard.db.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ciphers")
public class Cipher {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", length = 36)
    private String userUuid;

    @Column(name = "organization_uuid", length = 36)
    private String organizationUuid;

    @Column(name = "folder_uuid", length = 36)
    private String folderUuid;

    @Column(name = "type", nullable = false)
    private int type; // 1=Login, 2=SecureNote, 3=Card, 4=Identity

    @Column(name = "data", nullable = false, columnDefinition = "TEXT")
    private String data; // JSON

    @Column(name = "name", columnDefinition = "TEXT")
    private String name;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "fields", columnDefinition = "TEXT")
    private String fields;

    @Column(name = "password_history", columnDefinition = "TEXT")
    private String passwordHistory;

    @Column(name = "key", columnDefinition = "TEXT")
    private String key;

    @Column(name = "reprompt", nullable = false)
    private int reprompt = 0;

    @Column(name = "deleted_date")
    private Instant deletedDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getOrganizationUuid() { return organizationUuid; }
    public void setOrganizationUuid(String organizationUuid) { this.organizationUuid = organizationUuid; }
    public String getFolderUuid() { return folderUuid; }
    public void setFolderUuid(String folderUuid) { this.folderUuid = folderUuid; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getFields() { return fields; }
    public void setFields(String fields) { this.fields = fields; }
    public String getPasswordHistory() { return passwordHistory; }
    public void setPasswordHistory(String passwordHistory) { this.passwordHistory = passwordHistory; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public int getReprompt() { return reprompt; }
    public void setReprompt(int reprompt) { this.reprompt = reprompt; }
    public Instant getDeletedDate() { return deletedDate; }
    public void setDeletedDate(Instant deletedDate) { this.deletedDate = deletedDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
