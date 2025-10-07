package com.onemount.javahexagonal.application.service;

import com.onemount.javahexagonal.application.dto.response.UserDto;
import org.springframework.data.domain.Page;

public interface UserService {
    Page<UserDto> getUsers(Integer page, Integer size, String sort, String direction);
}
