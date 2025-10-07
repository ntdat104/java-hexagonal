package com.onemount.javahexagonal.infrastructure.repo;

import com.onemount.javahexagonal.domain.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaContactRepository extends JpaRepository<Contact, Long> {
    Optional<Contact> findByEmail(String email);
    Optional<Contact> findByPhoneNumber(String phoneNumber);
}
