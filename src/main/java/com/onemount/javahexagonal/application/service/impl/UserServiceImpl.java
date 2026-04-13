package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.converter.UserConverter;
import com.onemount.javahexagonal.application.dto.RestPage;
import com.onemount.javahexagonal.application.dto.response.UserDto;
import com.onemount.javahexagonal.application.enums.StatusEnums;
import com.onemount.javahexagonal.application.service.UserService;
import com.onemount.javahexagonal.domain.model.User;
import com.onemount.javahexagonal.domain.repo.UserRepo;
import com.onemount.javahexagonal.infrastructure.anotation.BumpVersion;
import com.onemount.javahexagonal.infrastructure.anotation.VersionCache;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.UUID;

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

    @PostConstruct
    public void init() {
        var userList = new ArrayList<User>();
        for (int i = 0; i < 1000; i++) {
            var user = new User();
            user.setUuid(UUID.randomUUID().toString());
            user.setFullName("User" + i);
            user.setEmail("User" + i);
            user.setPhoneNumber(UUID.randomUUID().toString());
            user.setHashedPassword(UUID.randomUUID().toString());
            user.setImageUrl(UUID.randomUUID().toString());
            user.setReferralCode(UUID.randomUUID().toString());
            user.setReferralByCode(UUID.randomUUID().toString());
            user.setStatus(StatusEnums.ACTIVE);
            userList.add(user);
        }
        userRepo.saveAll(userList);
    }

    // Dành cho User cụ thể
    // @VersionCache(entity = "order", userId = "#orderRequest.customerId")

    // Dành cho dữ liệu dùng chung (Shared)
    // @VersionCache(entity = "category", userId = "'GLOBAL'")

    @Override
//    @Cacheable(cacheNames = CACHE_NAME, key = "{#root.methodName, #page, #size, #sort, #direction}")
    @VersionCache(entity = CACHE_NAME, userId = "'GLOBAL'", extraKeys = {"#page", "#size", "#sort", "#direction"})
    public RestPage<UserDto> getUsers(Integer page, Integer size, String sort, String direction) {
        Page<UserDto> result = super.getAll(page, size, sort, direction);
        return new RestPage<>(result.getContent(), result.getPageable(), result.getTotalElements());
    }

    @Override
    @BumpVersion(entity = CACHE_NAME, userId = "'GLOBAL'")
    public void clear() {}

//    @Override
//    @VersionCache(
//            entity = "user",
//            key = "#userId",
//            extraKeys = {"#page", "#size", "#sort", "#direction"}
//    )
//    public Page<UserDto> getUsers(Long userId, Integer page, Integer size, String sort, String direction) {
//        return userRepo.findAllByUserId(userId, PageRequest.of(page, size, Sort.by(direction, sort)))
//                .map(userConverter::toDto);
//    }
//
//    extraKeys = {"#request.page", "#request.query"}
//
//    @VersionCache(entity = "wallet", key = "#userId")
//    public WalletDto getWallet(Long userId) { ... }
//
//    @BumpVersion(entity = "wallet", key = "#userId")
//    public void deposit(Long userId, DepositDto dto) { ... }
//
//    @VersionCache(entity = "product", key = "#userId", extraKey = "#page")
//    public Page<ProductDto> getFavorites(Long userId, int page) { ... }
//
//    // Nếu userId nằm bên trong một Object khác
//    @BumpVersion(entity = "product", key = "#request.ownerId")
//    public void updateProduct(ProductRequest request) { ... }
}
