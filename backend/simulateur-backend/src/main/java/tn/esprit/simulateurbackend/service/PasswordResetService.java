package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.ResetPasswordRequest;
import tn.esprit.simulateurbackend.entity.PasswordResetToken;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.PasswordResetTokenRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;

    private static final int TOKEN_EXPIRATION_MINUTES = 30;

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;

    private final PasswordResetTokenRepository resetTokens;

    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom =
            new SecureRandom();


    public PasswordResetService(
            UserRepository users,
            PasswordResetTokenRepository resetTokens,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
    }


    /**
     * Crée un token de réinitialisation uniquement si :
     *
     * - l'utilisateur existe
     * - son compte est actif
     *
     * IMPORTANT :
     * Le contrôleur ne devra jamais révéler
     * si l'adresse e-mail existe ou non.
     *
     * Le token brut retourné ici sera destiné
     * uniquement au futur service d'envoi d'e-mail.
     */
    @Transactional
    public Optional<String> createResetToken(
            String rawEmail
    ) {

        String email = normalizeEmail(rawEmail);

        Optional<User> optionalUser =
                users.findByEmail(email);

        /*
         * Anti-enumeration :
         *
         * On ne lance pas d'erreur si l'utilisateur
         * n'existe pas.
         */
        if (optionalUser.isEmpty()) {
            return Optional.empty();
        }

        User user = optionalUser.get();

        /*
         * Même comportement pour un compte désactivé.
         */
        if (!user.isEnabled()) {
            return Optional.empty();
        }


        /*
         * Un seul token de reset actif
         * par utilisateur.
         */
        resetTokens.deleteAllByUser(user);


        String rawToken =
                generateSecureToken();

        String tokenHash =
                hashToken(rawToken);


        PasswordResetToken resetToken =
                new PasswordResetToken();

        resetToken.setUser(user);

        resetToken.setTokenHash(
                tokenHash
        );

        resetToken.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(
                                TOKEN_EXPIRATION_MINUTES
                        )
        );


        resetTokens.save(
                resetToken
        );


        /*
         * Le token brut n'est jamais enregistré en BDD.
         *
         * Il sera utilisé uniquement pour générer
         * le lien envoyé par e-mail.
         */
        return Optional.of(
                rawToken
        );
    }


    /**
     * Réinitialise le mot de passe à partir
     * d'un token temporaire.
     */
    @Transactional
    public void resetPassword(
            ResetPasswordRequest request
    ) {

        /*
         * 1. Vérification confirmation
         */
        if (!request.newPassword()
                .equals(
                        request.confirmPassword()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La confirmation du mot de passe ne correspond pas."
            );
        }


        /*
         * 2. Limite réelle BCrypt
         */
        validateBcryptLength(
                request.newPassword()
        );


        /*
         * 3. Hash du token fourni
         */
        String tokenHash =
                hashToken(
                        request.token()
                );


        /*
         * On ne recherche jamais le token brut.
         */
        PasswordResetToken resetToken =
                resetTokens
                        .findByTokenHash(
                                tokenHash
                        )
                        .orElseThrow(
                                this::invalidToken
                        );


        /*
         * 4. Token déjà utilisé ?
         */
        if (resetToken.isUsed()) {

            throw invalidToken();
        }


        /*
         * 5. Token expiré ?
         */
        if (resetToken.isExpired()) {

            throw invalidToken();
        }


        User user =
                resetToken.getUser();


        /*
         * 6. Compte toujours actif ?
         */
        if (!user.isEnabled()) {

            throw invalidToken();
        }


        /*
         * 7. Empêcher la réutilisation immédiate
         * du mot de passe actuel.
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
         * 8. BCrypt
         */
        String encodedPassword =
                passwordEncoder.encode(
                        request.newPassword()
                );


        user.setPassword(
                encodedPassword
        );


        /*
         * 9. Marquer le token comme utilisé
         */
        resetToken.setUsedAt(
                LocalDateTime.now()
        );


        users.save(
                user
        );

        resetTokens.save(
                resetToken
        );
    }


    /**
     * Génère un token cryptographiquement
     * aléatoire de 256 bits.
     */
    private String generateSecureToken() {

        byte[] bytes =
                new byte[TOKEN_BYTES];

        secureRandom.nextBytes(
                bytes
        );

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        bytes
                );
    }


    /**
     * SHA-256 du token.
     *
     * Résultat :
     * 64 caractères hexadécimaux.
     */
    private String hashToken(
            String rawToken
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            StringBuilder hex =
                    new StringBuilder(
                            hash.length * 2
                    );


            for (byte value : hash) {

                hex.append(
                        String.format(
                                "%02x",
                                value & 0xff
                        )
                );
            }


            return hex.toString();


        } catch (NoSuchAlgorithmException exception) {

            /*
             * SHA-256 est garanti par la JVM.
             * Cette erreur représenterait donc
             * une configuration JVM anormale.
             */
            throw new IllegalStateException(
                    "SHA-256 indisponible.",
                    exception
            );
        }
    }


    private String normalizeEmail(
            String rawEmail
    ) {

        return rawEmail
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }


    private void validateBcryptLength(
            String password
    ) {

        int byteLength =
                password
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
                        .length;


        if (byteLength > BCRYPT_MAX_BYTES) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le nouveau mot de passe ne doit pas dépasser 72 octets."
            );
        }
    }


    /**
     * Même message pour :
     *
     * - token inexistant
     * - token expiré
     * - token déjà utilisé
     * - compte désactivé
     *
     * Cela évite de fournir des détails inutiles.
     */
    private ResponseStatusException invalidToken() {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Le lien de réinitialisation est invalide ou a expiré."
        );
    }
}