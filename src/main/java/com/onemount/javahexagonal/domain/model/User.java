package com.onemount.javahexagonal.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "users", indexes = {
        @Index(columnList = "email"),
        @Index(columnList = "user_name"),
        @Index(columnList = "phone_number")
})
public class User extends BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Size(min = 1, max = 50)
    @Column(name = "full_name")
    private String fullName;

    @Column(name = "is_male", nullable = false)
    private boolean isMale;

    @Email
    @Size(min = 5, max = 254)
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "user_name", nullable = false, unique = true)
    private String userName;

    @Size(max = 30)
    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "hashed_password", nullable = false)
    private String hashedPassword;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "referral_code", nullable = false, unique = true)
    private String referralCode;

    @Column(name = "referral_by_code")
    private String referralByCode;

    @Column(name = "permissions", nullable = false)
    private String permissions;

    @Column(name = "status")
    private String status;
}
