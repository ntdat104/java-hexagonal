package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.converter.UserConverter;
import com.onemount.javahexagonal.application.dto.response.UserDto;
import com.onemount.javahexagonal.application.service.UserService;
import com.onemount.javahexagonal.domain.model.User;
import com.onemount.javahexagonal.domain.repo.UserRepo;
import com.onemount.javahexagonal.infrastructure.anotation.AutoVersionCache;
import com.onemount.javahexagonal.infrastructure.anotation.BumpVersion;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends BaseServiceImpl<User, UserDto, Long> implements UserService {

    private static final String CACHE_NAME = "users";

    private final UserRepo userRepo;
    private final UserConverter userConverter;

    public UserServiceImpl(UserRepo userRepo, UserConverter userConverter) {
        this.userRepo = userRepo;
        this.userConverter = userConverter;
    }

    @Override
    protected UserRepo getRepository() {
        return userRepo;
    }

    @Override
    protected UserConverter getConverter() {
        return userConverter;
    }

    @Override
    @Cacheable(cacheNames = CACHE_NAME, key = "{#root.methodName, #page, #size, #sort, #direction}")
    public Page<UserDto> getUsers(Integer page, Integer size, String sort, String direction) {
        return super.getAll(page, size, sort, direction);
    }

    @Override
    @AutoVersionCache(
            entity = "user",
            key = "#userId",
            extraKeys = {"#page", "#size", "#sort", "#direction"}
    )
    public Page<UserDto> getUsers(Long userId, Integer page, Integer size, String sort, String direction) {
        return userRepo.findAllByUserId(userId, PageRequest.of(page, size, Sort.by(direction, sort)))
                .map(userConverter::toDto);
    }

    extraKeys = {"#request.page", "#request.query"}

    @AutoVersionCache(entity = "wallet", key = "#userId")
    public WalletDto getWallet(Long userId) { ... }

    @BumpVersion(entity = "wallet", key = "#userId")
    public void deposit(Long userId, DepositDto dto) { ... }

    @AutoVersionCache(entity = "product", key = "#userId", extraKey = "#page")
    public Page<ProductDto> getFavorites(Long userId, int page) { ... }

    // Nếu userId nằm bên trong một Object khác
    @BumpVersion(entity = "product", key = "#request.ownerId")
    public void updateProduct(ProductRequest request) { ... }
}
