package com.onemount.javahexagonal.application.dto.response;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
@Accessors(chain = true)
public class ContactDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String uuid;
    private String fullName;
    private Boolean isMale;
    private String email;
    private String phoneNumber;
    private String imageUrl;
}
