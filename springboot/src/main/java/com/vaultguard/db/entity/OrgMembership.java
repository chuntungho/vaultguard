package com.vaultguard.db.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users_organizations",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_uuid", "org_uuid"}))
public class OrgMembership {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Column(name = "org_uuid", nullable = false, length = 36)
    private String orgUuid;

    // atype: 0=Owner, 1=Admin, 2=User, 3=Manager, 4=Custom
    @Column(name = "atype", nullable = false)
    private int atype;

    // status: 0=Invited, 1=Accepted, 2=Confirmed
    @Column(name = "status", nullable = false)
    private int status;

    @Column(name = "access_all", nullable = false)
    private boolean accessAll = false;

    @Column(name = "akey", columnDefinition = "TEXT")
    private String akey;

    @Column(name = "reset_password_key", columnDefinition = "TEXT")
    private String resetPasswordKey;

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUserUuid() { return userUuid; }
    public void setUserUuid(String userUuid) { this.userUuid = userUuid; }
    public String getOrgUuid() { return orgUuid; }
    public void setOrgUuid(String orgUuid) { this.orgUuid = orgUuid; }
    public int getAtype() { return atype; }
    public void setAtype(int atype) { this.atype = atype; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public boolean isAccessAll() { return accessAll; }
    public void setAccessAll(boolean accessAll) { this.accessAll = accessAll; }
    public String getAkey() { return akey; }
    public void setAkey(String akey) { this.akey = akey; }
    public String getResetPasswordKey() { return resetPasswordKey; }
    public void setResetPasswordKey(String resetPasswordKey) { this.resetPasswordKey = resetPasswordKey; }
}
