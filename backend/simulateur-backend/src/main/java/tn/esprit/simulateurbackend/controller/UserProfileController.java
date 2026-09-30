package tn.esprit.simulateurbackend.controller;
import org.springframework.http.HttpStatus;
import tn.esprit.simulateurbackend.dto.ChangePasswordRequest;
import tn.esprit.simulateurbackend.service.ChangePasswordService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.esprit.simulateurbackend.dto.UpdateProfileRequest;
import tn.esprit.simulateurbackend.dto.UserProfileResponse;
import tn.esprit.simulateurbackend.service.UserProfileService;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final ChangePasswordService changePasswordService;
    private final UserProfileService userProfileService;

    public UserProfileController(
            UserProfileService userProfileService,
            ChangePasswordService changePasswordService
    ) {
        this.userProfileService = userProfileService;
        this.changePasswordService = changePasswordService;
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
}