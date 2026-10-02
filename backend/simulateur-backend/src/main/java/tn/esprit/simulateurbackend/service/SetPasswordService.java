package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.dto.SetPasswordRequest;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class SetPasswordService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public SetPasswordService(
            UserRepository users,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void setPassword(
            Authentication authentication,
            SetPasswordRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        /*
         * Sécurité :
         *
         * cet endpoint sert uniquement à créer le PREMIER
         * mot de passe local d'un compte OAuth/Google.
         *
         * Si un mot de passe existe déjà, l'utilisateur
         * doit passer par l'endpoint de changement classique.
         */
        if (
                user.getPassword() != null
                        && !user.getPassword().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Un mot de passe local existe déjà pour ce compte."
            );
        }

        String newPassword = request.newPassword();
        String confirmPassword = request.confirmPassword();

        if (!newPassword.equals(confirmPassword)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La confirmation du mot de passe ne correspond pas."
            );
        }

        if (newPassword.length() < 8) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir au moins 8 caractères."
            );
        }

        int passwordBytes =
                newPassword
                        .getBytes(StandardCharsets.UTF_8)
                        .length;

        if (passwordBytes > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe dépasse la limite de 72 octets autorisée."
            );
        }

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        users.save(user);
    }

    private User getAuthenticatedUser(
            Authentication authentication
    ) {

        if (
                authentication == null
                        || !authentication.isAuthenticated()
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

        return users
                .findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Utilisateur introuvable."
                        )
                );
    }
}