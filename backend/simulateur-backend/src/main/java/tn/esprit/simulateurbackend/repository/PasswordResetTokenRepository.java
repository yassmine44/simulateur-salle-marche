package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.simulateurbackend.entity.PasswordResetToken;
import tn.esprit.simulateurbackend.entity.User;

import java.util.Optional;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(
            String tokenHash
    );

    boolean existsByTokenHash(
            String tokenHash
    );

    void deleteAllByUser(
            User user
    );
}