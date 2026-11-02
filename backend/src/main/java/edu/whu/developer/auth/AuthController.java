package edu.whu.developer.auth;

import edu.whu.developer.auth.AuthService.AuthResponse;
import edu.whu.developer.auth.AuthService.LoginRequest;
import edu.whu.developer.auth.AuthService.OtpRequest;
import edu.whu.developer.auth.AuthService.OtpResponse;
import edu.whu.developer.auth.AuthService.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/otp/send")
    public OtpResponse send(@Valid @RequestBody OtpRequest request) {
        return auth.sendOtp(request);
    }

    @PostMapping("/auth/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.me();
    }
}
