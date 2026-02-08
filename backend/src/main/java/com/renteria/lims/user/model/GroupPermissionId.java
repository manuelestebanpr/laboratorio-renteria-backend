package com.renteria.lims.user.model;

import java.io.Serializable;
import java.util.UUID;

public class GroupPermissionId implements Serializable {
    private UUID groupId;
    private UUID permissionId;

    public GroupPermissionId() {}

    public GroupPermissionId(UUID groupId, UUID permissionId) {
        this.groupId = groupId;
        this.permissionId = permissionId;
    }

    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }
    public UUID getPermissionId() { return permissionId; }
    public void setPermissionId(UUID permissionId) { this.permissionId = permissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GroupPermissionId that = (GroupPermissionId) o;
        return groupId.equals(that.groupId) && permissionId.equals(that.permissionId);
    }

    @Override
    public int hashCode() {
        return 31 * groupId.hashCode() + permissionId.hashCode();
    }
}
