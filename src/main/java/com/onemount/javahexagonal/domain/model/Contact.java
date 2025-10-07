package com.onemount.javahexagonal.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "contacts", indexes = {
        @Index(columnList = "email"),
        @Index(columnList = "phone_number")
})
public class Contact extends BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "is_male", nullable = false)
    private Boolean isMale;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "image_url")
    private String imageUrl;
}
