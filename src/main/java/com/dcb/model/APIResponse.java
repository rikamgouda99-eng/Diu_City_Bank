package com.dcb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class APIResponse<T>{
    private Boolean isSuccess;
    private T data;
    private String message;
    private String timestamp;
}
