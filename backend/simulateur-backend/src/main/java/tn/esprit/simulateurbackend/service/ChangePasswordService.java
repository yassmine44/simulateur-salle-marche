package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.dto.ChangePasswordRequest;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class ChangePasswordService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public ChangePasswordService(
            UserRepository users,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void changePassword(
            Authentication authentication,
            ChangePasswordRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        /*
         * 1. Vérification du mot de passe actuel
         */
        if (!passwordEncoder.matches(
                request.currentPassword(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe actuel est incorrect."
            );
        }

        /*
         * 2. Vérification de la confirmation
         */
        if (!request.newPassword().equals(
                request.confirmPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La confirmation du nouveau mot de passe ne correspond pas."
            );
        }

        /*
         * 3. Limite réelle BCrypt :
         *    72 octets UTF-8, pas simplement 72 caractères.
         */
        validateBcryptLength(
                request.newPassword()
        );

        /*
         * 4. Empêcher de réutiliser immédiatement
         *    le mot de passe actuel.
         */
        if (passwordEncoder.matches(
                request.newPassword(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe doit être différent du mot de passe actuel."
            );
        }

        /*
         * 5. Hash BCrypt du nouveau mot de passe
         */
        String encodedPassword =
                passwordEncoder.encode(
                        request.newPassword()
                );

        user.setPassword(encodedPassword);

        users.save(user);
    }

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (
                authentication == null ||
                        !authentication.isAuthenticated()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié."
            );
        }

        String email = authentication
                .getName()
                .trim()
                .toLowerCase(Locale.ROOT);

        return users.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Utilisateur introuvable."
                        )
                );
    }

    private void validateBcryptLength(
            String password
    ) {

        int passwordBytes =
                password
                        .getBytes(StandardCharsets.UTF_8)
                        .length;

        if (passwordBytes > BCRYPT_MAX_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe ne doit pas dépasser 72 octets."
            );
        }
    }
}