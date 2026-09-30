package tn.esprit.simulateurbackend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.simulateurbackend.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
