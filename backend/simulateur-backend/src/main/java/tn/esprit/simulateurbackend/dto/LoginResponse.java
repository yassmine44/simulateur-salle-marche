package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;

public record LoginResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String countryCode,
        String phoneNumber,
        Role role,
        boolean hasLocalPassword
) {

    public static LoginResponse from(User user) {

        boolean hasLocalPassword =
                user.getPassword() != null
                        && !user.getPassword().isBlank();

        return new LoginResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCountryCode(),
                user.getPhoneNumber(),
                user.getRole(),
                hasLocalPassword
        );
    }
}