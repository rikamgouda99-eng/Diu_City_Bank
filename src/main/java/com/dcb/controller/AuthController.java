package com.dcb.controller;

import com.dcb.model.LoginRequest;
import com.dcb.model.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dcb/v1")

public class AuthController
{
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody
            @Valid
            LoginRequest request)
    {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(Util.mapToGeneralResponse(response,"200","AUTHENTICATED"));
    }
}
