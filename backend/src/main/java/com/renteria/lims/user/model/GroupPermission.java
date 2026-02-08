package com.renteria.lims.user.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "group_permissions")
@IdClass(GroupPermissionId.class)
public class GroupPermission {

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Id
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    public GroupPermission() {}

    public GroupPermission(UUID groupId, UUID permissionId) {
        this.groupId = groupId;
        this.permissionId = permissionId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }
}
