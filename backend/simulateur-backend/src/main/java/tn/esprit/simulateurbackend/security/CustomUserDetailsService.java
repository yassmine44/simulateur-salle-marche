package tn.esprit.simulateurbackend.security;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public CustomUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = users
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid credentials"
                        )
                );

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(
                        user.getPassword() != null
                                ? user.getPassword()
                                : "{noop}OAUTH2_ONLY_ACCOUNT"
                )
                .roles(user.getRole().name())
                .disabled(!user.isEnabled())
                .build();
    }
}