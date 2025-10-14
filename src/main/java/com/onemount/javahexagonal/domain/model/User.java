package com.onemount.javahexagonal.domain.model;

import com.onemount.javahexagonal.application.enums.StatusEnums;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "users", indexes = {
        @Index(columnList = "email"),
        @Index(columnList = "phone_number")
})
public class User extends AbstractModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

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

    @Column(name = "status")
    private StatusEnums status;
}
