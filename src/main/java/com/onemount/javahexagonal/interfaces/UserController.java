package com.onemount.javahexagonal.interfaces;

import com.onemount.javahexagonal.application.constant.UrlExternal;
import com.onemount.javahexagonal.application.dto.response.BasePageResponse;
import com.onemount.javahexagonal.application.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(UrlExternal.USER_PATH)
    public BasePageResponse<?> getUsers(@RequestParam(required = false, defaultValue = "0") Integer page,
                                        @RequestParam(required = false, defaultValue = "10") Integer size,
                                        @RequestParam(required = false) String sort,
                                        @RequestParam(required = false) String direction) {
        return BasePageResponse.ofSucceeded(userService.getUsers(page, size, sort, direction));
    }
}
