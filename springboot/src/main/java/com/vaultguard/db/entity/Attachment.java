package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "cipher_uuid", nullable = false, length = 36)
    private String cipherUuid;

    @Column(name = "file_name", nullable = false, columnDefinition = "TEXT")
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "akey", columnDefinition = "TEXT")
    private String akey;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCipherUuid() { return cipherUuid; }
    public void setCipherUuid(String cipherUuid) { this.cipherUuid = cipherUuid; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getAkey() { return akey; }
    public void setAkey(String akey) { this.akey = akey; }
}
