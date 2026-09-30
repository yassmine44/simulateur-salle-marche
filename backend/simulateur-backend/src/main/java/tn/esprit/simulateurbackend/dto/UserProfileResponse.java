package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;

public record UserProfileResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String countryCode,
        String phoneNumber,
        Role role,
        boolean enabled
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCountryCode(),
                user.getPhoneNumber(),
                user.getRole(),
                user.isEnabled()
        );
    }
}