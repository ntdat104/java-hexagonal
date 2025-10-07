package com.onemount.javahexagonal.domain.repo;

import com.onemount.javahexagonal.domain.model.Contact;

import java.util.List;
import java.util.Optional;

public interface ContactRepo {
    List<Contact> findAll();

    Optional<Contact> findById(Long id);
    Optional<Contact> findByEmail(String email);
    Optional<Contact> findByPhoneNumber(String phoneNumber);

    Contact saveOrUpdate(Contact contact);
    void delete(Contact contact);
}
