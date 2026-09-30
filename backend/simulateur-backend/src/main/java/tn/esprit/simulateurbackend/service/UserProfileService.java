package tn.esprit.simulateurbackend.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.dto.UpdateProfileRequest;
import tn.esprit.simulateurbackend.dto.UserProfileResponse;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.util.Locale;

@Service
public class UserProfileService {

    private final UserRepository users;
    private final PhoneNumberUtil phoneNumberUtil;

    public UserProfileService(UserRepository users) {
        this.users = users;
        this.phoneNumberUtil = PhoneNumberUtil.getInstance();
    }


    /**
     * Retourne le profil de l'utilisateur actuellement connecté.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile(Authentication authentication) {

        User user = getAuthenticatedUser(authentication);

        return UserProfileResponse.from(user);
    }


    /**
     * Met à jour les informations personnelles autorisées.
     *
     * L'utilisateur ne peut PAS modifier ici :
     * - son email
     * - son rôle
     * - son statut enabled
     * - son mot de passe
     */
    @Transactional
    public UserProfileResponse updateCurrentProfile(
            Authentication authentication,
            UpdateProfileRequest request
    ) {

        User user = getAuthenticatedUser(authentication);

        String countryCode = normalizeCountryCode(request.countryCode());

        String normalizedPhone = normalizePhoneNumber(
                request.phoneNumber(),
                countryCode
        );

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setCountryCode(countryCode);
        user.setPhoneNumber(normalizedPhone);

        User savedUser = users.save(user);

        return UserProfileResponse.from(savedUser);
    }


    /**
     * Recherche l'utilisateur à partir de l'identité enregistrée
     * dans la session Spring Security.
     */
    private User getAuthenticatedUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
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
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));
    }


    /**
     * Normalise le code pays en ISO alpha-2 majuscule.
     */
    private String normalizeCountryCode(String rawCountryCode) {

        String countryCode = rawCountryCode
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!phoneNumberUtil
                .getSupportedRegions()
                .contains(countryCode)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le pays sélectionné n'est pas pris en charge."
            );
        }

        return countryCode;
    }


    /**
     * Vérifie le numéro selon le pays et le convertit au format E.164.
     *
     * Exemple :
     * TN + "22 123 456"
     *
     * devient :
     * +21622123456
     */
    private String normalizePhoneNumber(
            String rawPhoneNumber,
            String countryCode
    ) {

        try {

            var parsedNumber = phoneNumberUtil.parse(
                    rawPhoneNumber,
                    countryCode
            );

            if (!phoneNumberUtil.isValidNumberForRegion(
                    parsedNumber,
                    countryCode
            )) {

                throw invalidPhoneNumber();
            }

            return phoneNumberUtil.format(
                    parsedNumber,
                    PhoneNumberUtil.PhoneNumberFormat.E164
            );

        } catch (NumberParseException exception) {

            throw invalidPhoneNumber();
        }
    }


    private ResponseStatusException invalidPhoneNumber() {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Le numéro de téléphone est invalide pour le pays sélectionné."
        );
    }
}