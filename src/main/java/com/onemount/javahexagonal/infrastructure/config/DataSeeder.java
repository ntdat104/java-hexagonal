package com.onemount.javahexagonal.infrastructure.config;

import com.github.javafaker.Faker;
import com.onemount.javahexagonal.application.enums.StatusEnums;
import com.onemount.javahexagonal.domain.model.User;
import com.onemount.javahexagonal.domain.repo.UserRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;
import java.util.UUID;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner initDatabase(UserRepo userRepo) {
        return args -> {
            if (userRepo.count() == 0) { // only seed if empty
                Faker faker = new Faker();
                Random random = new Random();

                for (int i = 1; i <= 100; i++) {
                    userRepo.save(new User()
                            .setUuid(UUID.randomUUID().toString())
                            .setFullName(faker.name().fullName())
                            .setEmail(faker.internet().emailAddress())
                            .setPhoneNumber("090" + (1000000 + random.nextInt(9000000)))
                            .setHashedPassword(faker.internet().password())
                            .setImageUrl(faker.internet().avatar())
                            .setReferralCode("REF" + (1000 + i))
                            .setReferralByCode(i > 1 ? "REF" + (1000 + i - 1) : null)
                            .setStatus(random.nextBoolean() ? StatusEnums.ACTIVE : StatusEnums.INACTIVE)
                    );
                }

                System.out.println("✅ Seeded 10 random users into H2 database!");
            }
        };
    }

}
