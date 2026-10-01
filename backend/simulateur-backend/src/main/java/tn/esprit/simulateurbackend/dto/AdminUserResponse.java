package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.entity.Role;

import java.time.LocalDateTime;

public record AdminUserResponse(

        Long id,

        String firstName,

        String lastName,

        String email,

        String countryCode,

        String phoneNumber,

        Role role,

        boolean enabled,

        LocalDateTime createdAt

) {

    public static AdminUserResponse from(User user) {

        return new AdminUserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCountryCode(),
                user.getPhoneNumber(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}