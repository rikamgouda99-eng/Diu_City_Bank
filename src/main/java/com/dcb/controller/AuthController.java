package com.dcb.controller;

import com.dcb.util.Util;
import com.dcb.model.*;
import com.dcb.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final Util utility;

    public AuthController(AuthService authService, Util utility){
        this.authService = authService;
        this.utility = utility;
    }


    @PostMapping("/signup")
    public ResponseEntity<APIResponse<?>> doSignUp(
            @RequestBody @Valid SignupRequest signupRequest
            ){
        SignupResponse response = authService.doSignUp(signupRequest);
        return ResponseEntity.accepted().body(utility.getSuccessResponse(response, "A 6 digit OTP generated successfully, the otp is valid for 10 minutes only."));
    }

    @PostMapping("/otp")
    public ResponseEntity<APIResponse<?>> verifyUser(
            @RequestBody @Valid OtpRequest otpRequest
    ){
        authService.verifyOtp(otpRequest);
        return ResponseEntity.ok(utility.getSuccessResponse(null, "OTP Verified successfully, please login to continue."));
    }

    @PostMapping("/login")
    public ResponseEntity<APIResponse<?>> doLogin(
            @RequestBody @Valid LoginRequest loginRequest
    ){
        LoginResponse response = authService.doLogin(loginRequest);
        return ResponseEntity.ok(utility.getSuccessResponse(response, "Login Successful."));
    }

}
