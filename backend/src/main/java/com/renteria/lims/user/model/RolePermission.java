package com.renteria.lims.user.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
public class RolePermission {

    @Id
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Id
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    public RolePermission() {}

    public RolePermission(Role role, UUID permissionId) {
        this.role = role;
        this.permissionId = permissionId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }
}
