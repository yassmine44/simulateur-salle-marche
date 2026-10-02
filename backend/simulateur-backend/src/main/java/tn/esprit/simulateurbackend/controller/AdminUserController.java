package tn.esprit.simulateurbackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.AdminUserResponse;
import tn.esprit.simulateurbackend.dto.UpdateUserRoleRequest;
import tn.esprit.simulateurbackend.dto.UpdateUserStatusRequest;

import tn.esprit.simulateurbackend.entity.Role;

import tn.esprit.simulateurbackend.service.AdminUserService;


@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;


    public AdminUserController(
            AdminUserService adminUserService
    ) {

        this.adminUserService =
                adminUserService;
    }


    // =========================
    // LIST USERS
    // =========================

    @GetMapping
    public Page<AdminUserResponse> getUsers(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            Role role,

            @RequestParam(required = false)
            Boolean enabled,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size

    ) {

        return adminUserService.getUsers(
                search,
                role,
                enabled,
                page,
                size
        );
    }


    // =========================
    // GET USER DETAILS
    // =========================

    @GetMapping("/{userId}")
    public AdminUserResponse getUserById(
            @PathVariable Long userId
    ) {

        return adminUserService
                .getUserById(
                        userId
                );
    }


    // =========================
    // ENABLE / DISABLE
    // =========================

    @PatchMapping("/{userId}/status")
    public AdminUserResponse updateStatus(

            @PathVariable Long userId,

            @Valid
            @RequestBody
            UpdateUserStatusRequest request,

            Authentication authentication,

            HttpServletRequest httpRequest

    ) {

        return adminUserService.updateStatus(
                userId,
                request.enabled(),
                authentication,
                httpRequest
        );
    }


    // =========================
    // CHANGE ROLE
    // =========================

    @PatchMapping("/{userId}/role")
    public AdminUserResponse updateRole(

            @PathVariable Long userId,

            @Valid
            @RequestBody
            UpdateUserRoleRequest request,

            Authentication authentication,

            HttpServletRequest httpRequest

    ) {

        return adminUserService.updateRole(
                userId,
                request.role(),
                authentication,
                httpRequest
        );
    }
}