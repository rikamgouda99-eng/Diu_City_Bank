package com.dcb.model;


import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRequest {
    @Size(max = 250, message = "Comment can be at most 250 characters.")
    private String comment;
}
