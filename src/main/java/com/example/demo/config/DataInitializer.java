package com.example.demo.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.demo.entity.Role;
import com.example.demo.repository.RoleRepository;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepo;

    public DataInitializer(RoleRepository roleRepo) {
        this.roleRepo = roleRepo;
    }

    @Override
    public void run(String... args) {
        if (roleRepo.findByName("ROLE_USER") == null) {
            Role role = new Role();
            role.setName("ROLE_USER");
            roleRepo.save(role);
        }
        if (roleRepo.findByName("ROLE_ADMIN") == null) {
            Role role = new Role();
            role.setName("ROLE_ADMIN");
            roleRepo.save(role);
        }
    }
}
