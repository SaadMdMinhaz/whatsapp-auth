package com.example.demo.service;

import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.ChangePasswordRequest;
import com.example.demo.dto.ForgotPasswordRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RefreshTokenRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.dto.ResetPasswordRequest;
import com.example.demo.dto.UserProfileResponse;
import com.example.demo.entity.PasswordResetToken;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;
    private final RefreshTokenRepository refreshRepo;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final PasswordEncoder encoder;
    private final long refreshExp;

    @Autowired
    public AuthService(UserRepository userRepo, RoleRepository roleRepo,
                       JwtService jwtService, RefreshTokenService refreshService,
                       RefreshTokenRepository refreshRepo,
                       PasswordResetTokenRepository resetTokenRepo,
                       PasswordEncoder encoder,
                       @Value("${jwt.refresh-expiration}") long refreshExp) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
        this.refreshRepo = refreshRepo;
        this.resetTokenRepo = resetTokenRepo;
        this.encoder = encoder;
        this.refreshExp = refreshExp;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(encoder.encode(req.getPassword()));

        Role role = roleRepo.findByName("ROLE_USER");
        if (role == null) {
            role = new Role();
            role.setName("ROLE_USER");
            role = roleRepo.save(role);
        }
        user.getRoles().add(role);
        userRepo.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        refreshRepo.deleteByUser(user);
        refreshRepo.flush();
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest req) {
        RefreshToken oldToken = refreshService.validate(req.getRefreshToken());
        User user = oldToken.getUser();

        refreshRepo.delete(oldToken);
        refreshRepo.flush();
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(RefreshTokenRequest req) {
        RefreshToken rt = refreshRepo.findByToken(req.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        refreshRepo.delete(rt);
    }

    public UserProfileResponse getProfile(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
        );
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!encoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPassword(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        refreshRepo.deleteByUser(user);
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequest req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + req.getEmail()));

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setToken(UUID.randomUUID().toString());
        resetToken.setExpiryDate(new Date(System.currentTimeMillis() + 3600000));
        resetTokenRepo.save(resetToken);

        return resetToken.getToken();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken resetToken = resetTokenRepo.findByToken(req.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset token"));

        if (resetToken.getExpiryDate().before(new Date())) {
            resetTokenRepo.delete(resetToken);
            throw new IllegalArgumentException("Reset token expired");
        }

        User user = resetToken.getUser();
        user.setPassword(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        resetTokenRepo.delete(resetToken);
        refreshRepo.deleteByUser(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        String accessToken = jwtService.generateToken(user.getEmail(), authorities);
        RefreshToken refresh = refreshService.create(user, refreshExp);

        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new AuthResponse(
                accessToken,
                refresh.getToken(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                roles
        );
    }
}
