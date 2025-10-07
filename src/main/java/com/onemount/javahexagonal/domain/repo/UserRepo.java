package com.onemount.javahexagonal.domain.repo;

import com.onemount.javahexagonal.domain.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepo {
    List<User> findAll();

    Optional<User> findById(Long id);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);

    User saveOrUpdate(User user);
    void delete(User user);
}
