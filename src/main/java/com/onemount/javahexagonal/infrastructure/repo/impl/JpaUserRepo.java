package com.onemount.javahexagonal.infrastructure.repo.impl;

import com.onemount.javahexagonal.domain.model.User;
import com.onemount.javahexagonal.domain.repo.UserRepo;
import com.onemount.javahexagonal.infrastructure.repo.JpaUserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaUserRepo implements UserRepo {

    private final JpaUserRepository jpaUserRepository;

    public JpaUserRepo(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public List<User> findAll() {
        return jpaUserRepository.findAll();
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpaUserRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaUserRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaUserRepository.findByUserName(username);
    }

    @Override
    public User saveOrUpdate(User user) {
        return jpaUserRepository.save(user);
    }

    @Override
    public void delete(User user) {
        jpaUserRepository.delete(user);
    }
}
