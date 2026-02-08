package com.renteria.lims.user.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_groups")
@IdClass(UserGroupId.class)
public class UserGroup {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private UUID assignedBy;

    public UserGroup() {}

    public UserGroup(UUID userId, UUID groupId, UUID assignedBy) {
        this.userId = userId;
        this.groupId = groupId;
        this.assignedBy = assignedBy;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public void setGroupId(UUID groupId) {
        this.groupId = groupId;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }
}
