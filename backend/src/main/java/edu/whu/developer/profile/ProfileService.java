package edu.whu.developer.profile;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import edu.whu.developer.auth.UserRepository;
import edu.whu.developer.auth.UserResponse;
import edu.whu.developer.common.AuthSupport;
import edu.whu.developer.common.AuthUser;
import edu.whu.developer.dict.DictService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
public class ProfileService {

    private static final Set<String> FALLBACK_IDENTITIES = Set.of("本科生", "硕士生", "博士生");

    private final UserRepository users;
    private final DictService dicts;

    public ProfileService(UserRepository users, DictService dicts) {
        this.users = users;
        this.dicts = dicts;
    }

    @Transactional(readOnly = true)
    public UserResponse myResume() {
        AuthUser current = AuthSupport.requireRole(Role.STUDENT);
        return UserResponse.from(users.findById(current.getId()).orElseThrow());
    }

    @Transactional
    public UserResponse saveResume(ResumeRequest request) {
        AuthUser current = AuthSupport.requireRole(Role.STUDENT);
        User user = users.findById(current.getId()).orElseThrow();
        String identity = request.studentIdentity().trim();
        Set<String> identities = dicts.enabledValues("STUDENT_IDENTITY");
        if (identities.isEmpty()) {
            identities = FALLBACK_IDENTITIES;
        }
        if (!identities.contains(identity)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "在读身份不合法");
        }
        String bio = request.bio().trim();
        if (bio.length() < 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "个人简介至少 20 字");
        }
        user.setRealName(request.realName().trim());
        user.setSchool(request.school().trim());
        user.setCollege(request.college().trim());
        user.setMajor(request.major().trim());
        user.setStudentIdentity(identity);
        user.setSkills(null);
        user.setBio(bio);
        String fileUrl = blankToNull(request.resumeFileUrl());
        user.setResumeFileUrl(fileUrl);
        user.setResumeFileName(fileUrl == null ? null : blankToNull(request.resumeFileName()));
        return UserResponse.from(users.save(user));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record ResumeRequest(
            @NotBlank(message = "请填写真实姓名") @Size(max = 64) String realName,
            @NotBlank(message = "请填写学校") @Size(max = 64) String school,
            @NotBlank(message = "请填写学院") @Size(max = 64) String college,
            @NotBlank(message = "请填写专业") @Size(max = 64) String major,
            @NotBlank(message = "请选择在读身份") @Size(max = 16) String studentIdentity,
            @NotBlank(message = "请填写个人简介") String bio,
            @Size(max = 500) String resumeFileUrl,
            @Size(max = 255) String resumeFileName
    ) {
    }
}
