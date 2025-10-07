package com.onemount.javahexagonal.application.converter;

import com.onemount.javahexagonal.application.dto.request.ContactCreateReq;
import com.onemount.javahexagonal.application.dto.request.ContactUpdateReq;
import com.onemount.javahexagonal.application.dto.response.ContactDto;
import com.onemount.javahexagonal.domain.model.Contact;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContactConverter {

    public ContactDto toDto(Contact input) {
        return new ContactDto()
                .setId(input.getId())
                .setUuid(input.getUuid())
                .setFullName(input.getFullName())
                .setIsMale(input.getIsMale())
                .setEmail(input.getEmail())
                .setPhoneNumber(input.getPhoneNumber())
                .setImageUrl(input.getImageUrl());
    }

    public List<ContactDto> toDtoList(List<Contact> input) {
        return input.stream().map(this::toDto).toList();
    }

    public Contact toEntity(ContactCreateReq input) {
        return new Contact()
                .setFullName(input.getFullName())
                .setIsMale(input.getIsMale())
                .setEmail(input.getEmail())
                .setPhoneNumber(input.getPhoneNumber())
                .setImageUrl(input.getImageUrl());
    }

    public Contact toEntity(ContactUpdateReq input) {
        return new Contact()
                .setId(input.getId())
                .setFullName(input.getFullName())
                .setIsMale(input.getIsMale())
                .setEmail(input.getEmail())
                .setPhoneNumber(input.getPhoneNumber())
                .setImageUrl(input.getImageUrl());
    }

}
