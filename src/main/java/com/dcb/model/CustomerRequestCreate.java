package com.dcb.model;

import com.dcb.enums.RequestType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequestCreate {
    @NotNull(message = "Request type is mandatory.")
    private RequestType requestType;

    @NotBlank(message = "Subject is mandatory.")
    @Size(max = 120, message = "Subject can be at most 120 characters.")
    private String subject;

    @Size(max = 1000, message = "Description can be at most 1000 characters.")
    private String description;
}
