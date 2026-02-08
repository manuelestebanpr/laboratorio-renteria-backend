package com.renteria.lims.user.repository;

import com.renteria.lims.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.patientProfile WHERE u.id = :id")
    Optional<User> findByIdWithPatientProfile(@Param("id") UUID id);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.employeeProfile WHERE u.id = :id")
    Optional<User> findByIdWithEmployeeProfile(@Param("id") UUID id);
}
