package com.onemount.javahexagonal.application.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ContactCreateReq extends BaseRequest {
    @NotBlank(message = "{400001}")
    @Size(max = 100, message = "{400002}")
    private String fullName;

    @NotNull(message = "{400003}")
    private Boolean isMale;

    @NotBlank(message = "{400004}")
    @Email(message = "{400005}")
    private String email;

    @NotBlank(message = "{400006}")
    @Pattern(regexp = "^[0-9]{8,15}$", message = "{400007}")
    private String phoneNumber;

    @Size(max = 255, message = "{400008}")
    private String imageUrl;
}
