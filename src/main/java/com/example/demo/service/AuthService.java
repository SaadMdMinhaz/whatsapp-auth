package com.example.demo.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final JwtService jwtService;
    private final RefreshTokenService refreshService;
    private final PasswordEncoder encoder;
    private final long refreshExp;

    @Autowired
    public AuthService(UserRepository userRepo, RoleRepository roleRepo,
                       JwtService jwtService, RefreshTokenService refreshService,
                       PasswordEncoder encoder,
                       @Value("${jwt.refresh-expiration}") long refreshExp) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.jwtService = jwtService;
        this.refreshService = refreshService;
        this.encoder = encoder;
        this.refreshExp = refreshExp;
    }

    public AuthResponse register(RegisterRequest req) {

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

        return login(new LoginRequest(req.getEmail(), req.getPassword()));
    }

    public AuthResponse login(LoginRequest req) {

        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        String accessToken = jwtService.generateToken(user.getEmail());
        RefreshToken refresh = refreshService.create(user, refreshExp);

        return new AuthResponse(accessToken, refresh.getToken());
    }
}
