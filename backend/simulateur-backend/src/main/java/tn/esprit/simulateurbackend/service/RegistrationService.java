package tn.esprit.simulateurbackend.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.RegisterRequest;
import tn.esprit.simulateurbackend.dto.UserResponse;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

@Service
public class RegistrationService {

    private final UserRepository users;
    private final PasswordEncoder passwords;

    private final PhoneNumberUtil phoneNumberUtil =
            PhoneNumberUtil.getInstance();

    public RegistrationService(
            UserRepository users,
            PasswordEncoder passwords
    ) {
        this.users = users;
        this.passwords = passwords;
    }

    public UserResponse register(RegisterRequest request) {

        // BCrypt accepte au maximum 72 octets.
        if (request.password()
                .getBytes(StandardCharsets.UTF_8).length > 72) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe ne doit pas dépasser 72 octets UTF-8."
            );
        }

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (users.existsByEmail(email)) {
            throw duplicateEmail();
        }

        String countryCode = request.countryCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        String normalizedPhoneNumber =
                normalizePhoneNumber(
                        request.phoneNumber(),
                        countryCode
                );

        User user = new User();

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());

        user.setCountryCode(countryCode);
        user.setPhoneNumber(normalizedPhoneNumber);

        user.setEmail(email);

        user.setPassword(
                passwords.encode(request.password())
        );

        // Le client ne choisit jamais son rôle.
        user.setRole(Role.USER);

        user.setEnabled(true);

        try {

            return UserResponse.from(
                    users.saveAndFlush(user)
            );

        } catch (DataIntegrityViolationException exception) {

            if (users.existsByEmail(email)) {
                throw duplicateEmail();
            }

            throw exception;
        }
    }

    private String normalizePhoneNumber(
            String rawPhoneNumber,
            String countryCode
    ) {

        if (!phoneNumberUtil
                .getSupportedRegions()
                .contains(countryCode)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le pays sélectionné n'est pas pris en charge."
            );
        }

        try {

            Phonenumber.PhoneNumber parsedNumber =
                    phoneNumberUtil.parse(
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

    private ResponseStatusException duplicateEmail() {

        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cette adresse e-mail est déjà utilisée."
        );
    }

    private ResponseStatusException invalidPhoneNumber() {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Le numéro de téléphone est invalide pour le pays sélectionné."
        );
    }
}