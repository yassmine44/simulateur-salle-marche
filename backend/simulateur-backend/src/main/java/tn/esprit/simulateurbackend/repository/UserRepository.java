package tn.esprit.simulateurbackend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    // =========================
    // EMAIL
    // =========================

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);


    // =========================
    // ADMIN SEARCH
    // =========================

    @Query("""
            SELECT u
            FROM User u
            WHERE (
                CAST(:search AS String) IS NULL
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
            )
            AND (
                :role IS NULL
                OR u.role = :role
            )
            AND (
                :enabled IS NULL
                OR u.enabled = :enabled
            )
            """)
    Page<User> searchUsers(
            @Param("search") String search,
            @Param("role") Role role,
            @Param("enabled") Boolean enabled,
            Pageable pageable
    );
}