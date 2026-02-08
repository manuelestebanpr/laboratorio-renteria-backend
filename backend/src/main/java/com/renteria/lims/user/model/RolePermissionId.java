package com.renteria.lims.user.model;

import java.io.Serializable;
import java.util.UUID;

public class RolePermissionId implements Serializable {
    private Role role;
    private UUID permissionId;

    public RolePermissionId() {}

    public RolePermissionId(Role role, UUID permissionId) {
        this.role = role;
        this.permissionId = permissionId;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public UUID getPermissionId() { return permissionId; }
    public void setPermissionId(UUID permissionId) { this.permissionId = permissionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RolePermissionId that = (RolePermissionId) o;
        return role == that.role && permissionId.equals(that.permissionId);
    }

    @Override
    public int hashCode() {
        return 31 * role.hashCode() + permissionId.hashCode();
    }
}
