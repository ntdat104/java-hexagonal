package com.onemount.javahexagonal.application.service;

import com.onemount.javahexagonal.application.dto.request.ContactCreateReq;
import com.onemount.javahexagonal.application.dto.request.ContactUpdateReq;
import com.onemount.javahexagonal.application.dto.response.ContactDto;

import java.util.List;

public interface ContactService {
    List<ContactDto> findAll();
    ContactDto findById(Long id);
    ContactDto create(ContactCreateReq req);
    ContactDto update(ContactUpdateReq req);
    void delete(Long id);
}
