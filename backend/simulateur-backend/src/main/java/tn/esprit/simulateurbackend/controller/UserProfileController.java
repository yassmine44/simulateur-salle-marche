package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprit.simulateurbackend.dto.UpdateProfileRequest;
import tn.esprit.simulateurbackend.dto.UserProfileResponse;
import tn.esprit.simulateurbackend.service.UserProfileService;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    public UserProfileResponse getCurrentProfile(
            Authentication authentication
    ) {
        return userProfileService.getCurrentProfile(authentication);
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
}