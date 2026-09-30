package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String countryCode,
        String phoneNumber,
        String email,
        Role role,
        boolean enabled
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getCountryCode(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled()
        );
    }
}