package com.capacitix;

import com.capacitix.entity.User;
import com.capacitix.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class CapacitixApplication {

    public static void main(String[] args) {
        SpringApplication.run(CapacitixApplication.class, args);
    }

    /**
     * Create default Admin account when application starts.
     */
    @Bean
    CommandLineRunner createDefaultAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            String adminEmail = "admin1@capacitix.com";
            String adminPassword = "Admin@1234";

            /*
             * Check whether Admin already exists
             */
            if (userRepository.findByEmail(adminEmail).isEmpty()) {

                User admin = new User();

                // User details
                admin.setFullName("Capacitix Administrator");
                admin.setEmail(adminEmail);

                // Optional fields
                admin.setPhoneNumber(null);
                admin.setDepartment("Administration");
                admin.setDesignation("System Administrator");

                // Encrypt password before saving
                admin.setPassword(
                        passwordEncoder.encode(adminPassword)
                );

                // IMPORTANT: Role is an enum
                admin.setRole(User.Role.ADMIN);

                // Save Admin
                userRepository.save(admin);

                System.out.println();
                System.out.println("========================================");
                System.out.println("       DEFAULT ADMIN CREATED");
                System.out.println("========================================");
                System.out.println("Email    : " + adminEmail);
                System.out.println("Password : " + adminPassword);
                System.out.println("Role     : ADMIN");
                System.out.println("========================================");
                System.out.println();

            } else {

                System.out.println();
                System.out.println("========================================");
                System.out.println("       ADMIN ALREADY EXISTS");
                System.out.println("========================================");
                System.out.println("Email    : " + adminEmail);
                System.out.println("========================================");
                System.out.println();
            }
        };
    }
}