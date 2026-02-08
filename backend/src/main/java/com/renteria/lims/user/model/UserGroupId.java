package com.renteria.lims.user.model;

import java.io.Serializable;
import java.util.UUID;

public class UserGroupId implements Serializable {
    private UUID userId;
    private UUID groupId;

    public UserGroupId() {}

    public UserGroupId(UUID userId, UUID groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getGroupId() { return groupId; }
    public void setGroupId(UUID groupId) { this.groupId = groupId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserGroupId that = (UserGroupId) o;
        return userId.equals(that.userId) && groupId.equals(that.groupId);
    }

    @Override
    public int hashCode() {
        return 31 * userId.hashCode() + groupId.hashCode();
    }
}
