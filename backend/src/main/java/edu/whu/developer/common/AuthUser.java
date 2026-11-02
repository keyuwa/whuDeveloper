package edu.whu.developer.common;

import edu.whu.developer.auth.Role;
import edu.whu.developer.auth.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AuthUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String displayName;
    private final String realName;
    private final Role role;
    private final String abilityLevel;
    private final String passwordHash;

    public AuthUser(Long id, String email, String displayName, String realName, Role role, String abilityLevel, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.realName = realName;
        this.role = role;
        this.abilityLevel = abilityLevel;
        this.passwordHash = passwordHash;
    }

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRealName(), user.getRole(), user.getAbilityLevel(), user.getPasswordHash());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRealName() {
        return realName;
    }

    public Role getRole() {
        return role;
    }

    public String getAbilityLevel() {
        return abilityLevel;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
