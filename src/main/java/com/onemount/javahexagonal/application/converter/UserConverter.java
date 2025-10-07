package com.onemount.javahexagonal.application.converter;

import com.onemount.javahexagonal.application.dto.response.UserDto;
import com.onemount.javahexagonal.domain.model.User;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UserConverter extends AbstractConverter<User, UserDto> {
    @Override
    public UserDto convert(User input, Map<String, Object> parameters) {
        UserDto output = new UserDto();
        output.setId(input.getId());
        output.setUuid(input.getUuid());
        output.setFullName(input.getFullName());
        output.setEmail(input.getEmail());
        output.setPhoneNumber(input.getPhoneNumber());
        output.setImageUrl(input.getImageUrl());
        output.setReferralCode(input.getReferralCode());
        output.setReferralByCode(input.getReferralByCode());
        output.setStatus(input.getStatus());
        return output;
    }
}
