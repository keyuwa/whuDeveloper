package edu.whu.developer.profile;

import edu.whu.developer.auth.UserResponse;
import edu.whu.developer.profile.ProfileService.ResumeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/me/resume")
    public UserResponse getResume() {
        return profiles.myResume();
    }

    @PutMapping("/me/resume")
    public UserResponse saveResume(@Valid @RequestBody ResumeRequest request) {
        return profiles.saveResume(request);
    }
}
