package com.renteria.lims.user.repository;

import com.renteria.lims.user.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    @Query("""
        SELECT DISTINCT p.code FROM Permission p
        WHERE p.id IN (
            SELECT rp.permissionId FROM RolePermission rp WHERE rp.role = :role
            UNION
            SELECT gp.permissionId FROM GroupPermission gp 
            WHERE gp.groupId IN (
                SELECT ug.groupId FROM UserGroup ug WHERE ug.userId = :userId
            )
        )
        """)
    Set<String> findEffectivePermissions(@Param("userId") UUID userId, @Param("role") String role);
}
