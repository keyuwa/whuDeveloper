package edu.whu.developer.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.common.EmailDomain;
import edu.whu.developer.common.MailService;
import edu.whu.developer.config.JwtService;
import edu.whu.developer.notify.AuditService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class AuthService {

    private final UserRepository users;
    private final EmailOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MailService mail;
    private final AuditService audit;
    private final boolean exposeOtp;
    private final int ttlMinutes;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users,
                       EmailOtpRepository otps,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       MailService mail,
                       AuditService audit,
                       @Value("${app.otp.expose}") boolean exposeOtp,
                       @Value("${app.otp.ttl-minutes}") int ttlMinutes) {
        this.users = users;
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mail = mail;
        this.audit = audit;
        this.exposeOtp = exposeOtp;
        this.ttlMinutes = ttlMinutes;
    }

    @Transactional
    public OtpResponse sendOtp(OtpRequest request) {
        String email = EmailDomain.normalize(request.email());
        Role role = parseRole(request.role());
        ensureDomain(email, role);
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这个邮箱已经注册");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailOtp otp = new EmailOtp();
        otp.setEmail(email);
        otp.setPurpose("REGISTER");
        otp.setCodeHash(hash(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(ttlMinutes));
        otps.save(otp);
        mail.send(email, "WHU TOP Developer 注册验证码", "验证码 " + code + "，" + ttlMinutes + " 分钟内有效。");
        return new OtpResponse(true, exposeOtp ? code : null);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailDomain.normalize(request.email());
        Role role = parseRole(request.role());
        ensureDomain(email, role);
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "这个邮箱已经注册");
        }
        EmailOtp otp = otps.findTopByEmailAndPurposeAndConsumedAtIsNullOrderByIdDesc(email, "REGISTER")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先获取验证码"));
        if (otp.getExpiresAt().isBefore(LocalDateTime.now()) || !hash(request.code().trim()).equals(otp.getCodeHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码不正确或已过期");
        }
        otp.setConsumedAt(LocalDateTime.now());
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setEmailVerified(true);
        user.setStatus("ACTIVE");
        if (role == Role.STUDENT) {
            user.setDisplayName(request.displayName().trim());
            user.setAbilityLevel("L1");
        } else {
            if (request.realName() == null || request.realName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布方需要填写实名");
            }
            user.setRealName(request.realName().trim());
            user.setDisplayName(request.realName().trim());
        }
        users.save(user);
        audit.record(user.getId(), "USER", user.getId(), "REGISTER", role.name());
        return new AuthResponse(jwtService.issue(user.getEmail()), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = EmailDomain.normalize(request.email());
        User user = users.findByEmail(email)
                .filter(found -> "ACTIVE".equals(found.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
        }
        return new AuthResponse(jwtService.issue(user.getEmail()), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        AuthUser current = AuthSupport.current();
        User user = users.findById(current.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
        return UserResponse.from(user);
    }

    private void ensureDomain(String email, Role role) {
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理员账号不能自助注册");
        }
        if (role == Role.STUDENT && !EmailDomain.isStudent(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "学生邮箱需要使用 .edu.cn");
        }
        if (role == Role.PUBLISHER && !EmailDomain.isPublisher(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发布方邮箱需要使用 .whu.edu.cn");
        }
    }

    private Role parseRole(String raw) {
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色只能是学生或发布方");
        }
    }

    private String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record OtpRequest(@Email(message = "邮箱格式不正确") @NotBlank(message = "请填写邮箱") String email,
                             @NotBlank(message = "请选择角色") String role) {
    }

    public record RegisterRequest(
            @Email(message = "邮箱格式不正确") @NotBlank(message = "请填写邮箱") String email,
            @NotBlank(message = "请填写验证码") String code,
            @NotBlank(message = "请填写密码") @Size(min = 8, max = 64, message = "密码至少 8 位") String password,
            @NotBlank(message = "请选择角色") String role,
            @NotBlank(message = "请填写昵称或姓名") @Size(max = 64) String displayName,
            @Size(max = 64) String realName
    ) {
    }

    public record LoginRequest(
            @Email(message = "邮箱格式不正确") @NotBlank(message = "请填写邮箱") String email,
            @NotBlank(message = "请填写密码") String password
    ) {
    }

    public record AuthResponse(String token, UserResponse user) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OtpResponse(boolean sent, String devCode) {
    }
}
