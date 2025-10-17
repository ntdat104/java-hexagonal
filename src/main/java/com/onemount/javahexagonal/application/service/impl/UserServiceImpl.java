package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.converter.UserConverter;
import com.onemount.javahexagonal.application.dto.response.UserDto;
import com.onemount.javahexagonal.application.service.UserService;
import com.onemount.javahexagonal.domain.model.User;
import com.onemount.javahexagonal.domain.repo.UserRepo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private static final String CACHE_NAME = "users";

    private final UserRepo userRepo;
    private final UserConverter userConverter;

    public UserServiceImpl(UserRepo userRepo, UserConverter userConverter) {
        this.userRepo = userRepo;
        this.userConverter = userConverter;
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "{#root.methodName, #page, #size, #sort, #direction}")
    public Page<UserDto> getUsers(Integer page, Integer size, String sort, String direction) {
        Sort.Direction sortDirection = "DESC".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
        Page<User> userPage = userRepo.findAll(pageable);
        return userConverter.convertToPage(userPage);
    }
}
