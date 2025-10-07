package com.onemount.javahexagonal.application.service.impl;

import com.onemount.javahexagonal.application.converter.ContactConverter;
import com.onemount.javahexagonal.application.dto.request.ContactCreateReq;
import com.onemount.javahexagonal.application.dto.request.ContactUpdateReq;
import com.onemount.javahexagonal.application.dto.response.ContactDto;
import com.onemount.javahexagonal.application.service.ContactService;
import com.onemount.javahexagonal.domain.model.Contact;
import com.onemount.javahexagonal.domain.repo.ContactRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactServiceImpl implements ContactService {

    private final ContactRepo contactRepo;
    private final ContactConverter contactConverter;

    public ContactServiceImpl(
            ContactRepo contactRepo,
            ContactConverter contactConverter
    ) {
        this.contactRepo = contactRepo;
        this.contactConverter = contactConverter;
    }

    @Override
    public List<ContactDto> findAll() {
        List<Contact> contacts = contactRepo.findAll();
        return contactConverter.toDtoList(contacts);
    }

    @Override
    public ContactDto findById(Long id) {
        Contact contact = contactRepo.findById(id).orElseThrow(() -> {
            return new RuntimeException("Contact with id " + id + " not found");
        });
        return contactConverter.toDto(contact);
    }

    @Override
    public ContactDto create(ContactCreateReq req) {
        Contact contactCreateReq = contactConverter.toEntity(req);
        Contact contactSaved = contactRepo.saveOrUpdate(contactCreateReq);
        return contactConverter.toDto(contactSaved);
    }

    @Override
    public ContactDto update(ContactUpdateReq req) {
        Contact contactUpdateReq = contactConverter.toEntity(req);
        Contact contactSaved = contactRepo.saveOrUpdate(contactUpdateReq);
        return contactConverter.toDto(contactSaved);
    }

    @Override
    public void delete(Long id) {
        Contact contact = contactRepo.findById(id).orElseThrow(() -> {
            return new RuntimeException("Contact with id " + id + " not found");
        });
        contactRepo.delete(contact);
    }
}
