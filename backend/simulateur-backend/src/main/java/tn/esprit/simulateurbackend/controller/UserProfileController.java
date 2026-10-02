package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprit.simulateurbackend.dto.ChangePasswordRequest;
import tn.esprit.simulateurbackend.dto.SetPasswordRequest;
import tn.esprit.simulateurbackend.dto.UpdateProfileRequest;
import tn.esprit.simulateurbackend.dto.UserProfileResponse;
import tn.esprit.simulateurbackend.service.ChangePasswordService;
import tn.esprit.simulateurbackend.service.SetPasswordService;
import tn.esprit.simulateurbackend.service.UserProfileService;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final ChangePasswordService changePasswordService;
    private final SetPasswordService setPasswordService;
    private final UserProfileService userProfileService;

    public UserProfileController(
            UserProfileService userProfileService,
            ChangePasswordService changePasswordService,
            SetPasswordService setPasswordService
    ) {
        this.userProfileService = userProfileService;
        this.changePasswordService = changePasswordService;
        this.setPasswordService = setPasswordService;
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentProfile(
            Authentication authentication
    ) {
        return userProfileService
                .getCurrentProfile(authentication);
    }

    @PutMapping("/me")
    public UserProfileResponse updateCurrentProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {

        return userProfileService.updateCurrentProfile(
                authentication,
                request
        );
    }

    /*
     * Compte possédant déjà un mot de passe local.
     */
    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {

        changePasswordService.changePassword(
                authentication,
                request
        );
    }

    /*
     * Compte Google/OAuth sans mot de passe local.
     */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPassword(
            Authentication authentication,
            @Valid @RequestBody SetPasswordRequest request
    ) {

        setPasswordService.setPassword(
                authentication,
                request
        );
    }
}