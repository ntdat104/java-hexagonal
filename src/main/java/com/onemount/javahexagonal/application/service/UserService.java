package com.onemount.javahexagonal.application.service;

import com.onemount.javahexagonal.application.dto.RestPage;
import com.onemount.javahexagonal.application.dto.response.UserDto;

public interface UserService {
    RestPage<UserDto> getUsers(Integer page, Integer size, String sort, String direction);
    void clear();
}
