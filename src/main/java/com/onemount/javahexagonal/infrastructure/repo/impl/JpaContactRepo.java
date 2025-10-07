package com.onemount.javahexagonal.infrastructure.repo.impl;

import com.onemount.javahexagonal.domain.model.Contact;
import com.onemount.javahexagonal.domain.repo.ContactRepo;
import com.onemount.javahexagonal.infrastructure.repo.JpaContactRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaContactRepo implements ContactRepo {

    private final JpaContactRepository jpaContactRepository;

    public JpaContactRepo(JpaContactRepository jpaContactRepository) {
        this.jpaContactRepository = jpaContactRepository;
    }

    @Override
    public List<Contact> findAll() {
        return jpaContactRepository.findAll();
    }

    @Override
    public Optional<Contact> findById(Long id) {
        return jpaContactRepository.findById(id);
    }

    @Override
    public Optional<Contact> findByEmail(String email) {
        return jpaContactRepository.findByEmail(email);
    }

    @Override
    public Optional<Contact> findByPhoneNumber(String phoneNumber) {
        return jpaContactRepository.findByPhoneNumber(phoneNumber);
    }

    @Override
    public Contact saveOrUpdate(Contact contact) {
        return jpaContactRepository.save(contact);
    }

    @Override
    public void delete(Contact contact) {
        jpaContactRepository.delete(contact);
    }
}
