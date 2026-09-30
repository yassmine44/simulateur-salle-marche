package tn.esprit.simulateurbackend.service;

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

    public RegistrationService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public UserResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le mot de passe ne doit pas dépasser 72 octets UTF-8.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            throw duplicateEmail();
        }
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(email);
        user.setPassword(passwords.encode(request.password()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // saveAndFlush has its own transaction: after rollback, check for a concurrent signup.
            if (users.existsByEmail(email)) {
                throw duplicateEmail();
            }
            throw exception;
        }
    }

    private ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée.");
    }
}
