package tn.esprit.simulateurbackend.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;

import org.springframework.security.core.Authentication;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.AdminUserResponse;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.UserRepository;


@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private static final int MAX_PAGE_SIZE =
            50;


    private final UserRepository userRepository;

    private final AuditService auditService;


    public AdminUserService(
            UserRepository userRepository,
            AuditService auditService
    ) {

        this.userRepository =
                userRepository;

        this.auditService =
                auditService;
    }


    // =========================
    // LIST + SEARCH + FILTERS
    // =========================

    public Page<AdminUserResponse> getUsers(
            String search,
            Role role,
            Boolean enabled,
            int page,
            int size
    ) {

        int safePage =
                Math.max(
                        page,
                        0
                );


        int safeSize =
                Math.min(
                        Math.max(
                                size,
                                1
                        ),
                        MAX_PAGE_SIZE
                );


        String normalizedSearch =
                normalizeSearch(
                        search
                );


        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,

                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );


        return userRepository
                .searchUsers(
                        normalizedSearch,
                        role,
                        enabled,
                        pageable
                )
                .map(
                        AdminUserResponse::from
                );
    }


    // =========================
    // GET ONE USER
    // =========================

    public AdminUserResponse getUserById(
            Long userId
    ) {

        User user =
                findUserById(
                        userId
                );


        return AdminUserResponse.from(
                user
        );
    }


    // =========================
    // ENABLE / DISABLE USER
    // =========================

    @Transactional
    public AdminUserResponse updateStatus(
            Long userId,
            boolean enabled,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {

        User targetUser =
                findUserById(
                        userId
                );


        User currentAdmin =
                findCurrentUser(
                        authentication
                );


        /*
         * Un administrateur ne peut pas
         * se désactiver lui-même.
         */
        if (
                targetUser
                        .getId()
                        .equals(
                                currentAdmin.getId()
                        )
                        &&
                        !enabled
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vous ne pouvez pas désactiver votre propre compte."
            );
        }


        /*
         * Aucun changement nécessaire.
         */
        if (
                targetUser.isEnabled()
                        == enabled
        ) {

            return AdminUserResponse.from(
                    targetUser
            );
        }


        targetUser.setEnabled(
                enabled
        );


        User savedUser =
                userRepository.save(
                        targetUser
                );


        /*
         * =========================================
         * AUDIT
         * =========================================
         */

        AuditEventType eventType =
                enabled
                        ? AuditEventType.USER_ENABLED
                        : AuditEventType.USER_DISABLED;


        auditService.log(
                eventType,

                savedUser.getId(),

                savedUser.getEmail(),

                currentAdmin.getEmail(),

                true,

                enabled
                        ? "Compte utilisateur activé par un administrateur."
                        : "Compte utilisateur désactivé par un administrateur.",

                httpRequest
        );


        return AdminUserResponse.from(
                savedUser
        );
    }


    // =========================
    // CHANGE ROLE
    // =========================

    @Transactional
    public AdminUserResponse updateRole(
            Long userId,
            Role newRole,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {

        User targetUser =
                findUserById(
                        userId
                );


        User currentAdmin =
                findCurrentUser(
                        authentication
                );


        /*
         * Empêche un administrateur
         * de retirer son propre rôle ADMIN.
         */
        if (
                targetUser
                        .getId()
                        .equals(
                                currentAdmin.getId()
                        )
                        &&
                        newRole != Role.ADMIN
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vous ne pouvez pas retirer votre propre rôle administrateur."
            );
        }


        Role oldRole =
                targetUser.getRole();


        /*
         * Pas de changement réel.
         */
        if (
                oldRole == newRole
        ) {

            return AdminUserResponse.from(
                    targetUser
            );
        }


        targetUser.setRole(
                newRole
        );


        User savedUser =
                userRepository.save(
                        targetUser
                );


        /*
         * =========================================
         * AUDIT : ROLE_CHANGED
         * =========================================
         */

        auditService.log(
                AuditEventType.ROLE_CHANGED,

                savedUser.getId(),

                savedUser.getEmail(),

                currentAdmin.getEmail(),

                true,

                "Rôle modifié : "
                        + oldRole.name()
                        + " -> "
                        + newRole.name(),

                httpRequest
        );


        return AdminUserResponse.from(
                savedUser
        );
    }


    // =========================
    // HELPERS
    // =========================

    private User findUserById(
            Long userId
    ) {

        return userRepository
                .findById(
                        userId
                )
                .orElseThrow(() ->

                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Utilisateur introuvable."
                        )

                );
    }


    private User findCurrentUser(
            Authentication authentication
    ) {

        if (
                authentication == null
                        ||
                        !authentication.isAuthenticated()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Session invalide."
            );
        }


        return userRepository
                .findByEmailIgnoreCase(
                        authentication.getName()
                )
                .orElseThrow(() ->

                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Utilisateur authentifié introuvable."
                        )

                );
    }


    private String normalizeSearch(
            String search
    ) {

        if (
                search == null
                        ||
                        search.isBlank()
        ) {

            return null;
        }


        return search.trim();
    }
}