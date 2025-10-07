package com.onemount.javahexagonal.interfaces;

import com.onemount.javahexagonal.application.constant.UrlExternal;
import com.onemount.javahexagonal.application.dto.request.ContactCreateReq;
import com.onemount.javahexagonal.application.dto.request.ContactUpdateReq;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.service.ContactService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(UrlExternal.CONTACT_PATH)
public class ContactController {

    private final MessageSource messageSource;
    private final ContactService contactService;

    public ContactController(ContactService contactService, MessageSource messageSource) {
        this.contactService = contactService;
        this.messageSource = messageSource;
    }

    @GetMapping
    public BaseResponse<?> findAll() {
        String message = messageSource.getMessage(
                "400004",
                null, // Arguments for placeholders (if any)
                LocaleContextHolder.getLocale() // The current user's locale
        );
        return BaseResponse.ofSucceeded(contactService.findAll());
    }

    @GetMapping("{id}")
    public BaseResponse<?> findById(@PathVariable("id") Long id) {
        return BaseResponse.ofSucceeded(contactService.findById(id));
    }

    @PostMapping
    public BaseResponse<?> save(@Valid @RequestBody ContactCreateReq input) {
        return BaseResponse.ofSucceeded(contactService.create(input));
    }

    @PutMapping("{id}")
    public BaseResponse<?> update(@PathVariable("id") Long id, @Valid @RequestBody ContactUpdateReq input) {
        return BaseResponse.ofSucceeded(contactService.update(input));
    }

    @DeleteMapping("{id}")
    public BaseResponse<?> delete(@PathVariable("id") Long id) {
        contactService.delete(id);
        return BaseResponse.ofSucceeded();
    }

}
