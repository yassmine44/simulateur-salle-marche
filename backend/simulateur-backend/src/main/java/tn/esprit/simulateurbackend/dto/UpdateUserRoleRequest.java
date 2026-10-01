package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotNull;
import tn.esprit.simulateurbackend.entity.Role;

public record UpdateUserRoleRequest(

        @NotNull
        Role role

) {
}