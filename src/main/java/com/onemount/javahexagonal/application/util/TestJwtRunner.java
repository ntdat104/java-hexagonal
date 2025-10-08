package com.onemount.javahexagonal.application.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class TestJwtRunner implements CommandLineRunner {

    private final JwtUtils jwtUtils;

    public TestJwtRunner(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void run(String... args) {
        System.out.println("=== JWT TEST START ===");

        // Generate Access Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        String accessToken = jwtUtils.generateAccessToken("john_doe", claims);
        System.out.println("Access Token: " + accessToken);

        // Validate Access Token
        Jws<Claims> parsedAccess = jwtUtils.validate(accessToken);
        System.out.println("Validated Access Token:");
        System.out.println(" - Subject: " + parsedAccess.getBody().getSubject());
        System.out.println(" - Role: " + parsedAccess.getBody().get("role"));
        System.out.println(" - Expiration: " + parsedAccess.getBody().getExpiration());

        // Generate and Validate Refresh Token
        String refreshToken = jwtUtils.generateRefreshToken();
        System.out.println("\nRefresh Token: " + refreshToken);

        Jws<Claims> parsedRefresh = jwtUtils.validate(refreshToken);
        System.out.println("Validated Refresh Token:");
        System.out.println(" - Subject: " + parsedRefresh.getBody().getSubject());
        System.out.println(" - Expiration: " + parsedRefresh.getBody().getExpiration());

        // Extract username from Access Token
        String username = jwtUtils.getUsernameFromToken(accessToken);
        System.out.println("\nExtracted username: " + username);

        // Try invalid token
        try {
            jwtUtils.validate("invalid.token.value");
        } catch (Exception e) {
            System.out.println("\nInvalid token test passed: " + e.getMessage());
        }

        System.out.println("=== JWT TEST END ===");
    }

}
