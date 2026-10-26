package com.dcb.controller;

import com.dcb.service.OtpService;
import com.dcb.service.StatusCheckService;
import com.dcb.util.Util;
import com.dcb.model.*;
import com.dcb.service.AuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final Util utility;
    private final OtpService otpService;
    private final StatusCheckService statusCheckService;

    public AuthController(AuthService authService, Util utility, OtpService otpService, StatusCheckService statusCheckService){
        this.authService = authService;
        this.utility = utility;
        this.otpService = otpService;
        this.statusCheckService = statusCheckService;
    }


    @PostMapping("/signup")
    public ResponseEntity<APIResponse<?>> doSignUp(
            @RequestBody @Valid SignupRequest signupRequest
            ){
        SignupResponse response = authService.doSignUp(signupRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(utility.getSuccessResponse(response, "A 6 digit OTP generated successfully, the otp is valid for 10 minutes only."));
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

    //re-generate an OTP
    @GetMapping("/otp")
    public ResponseEntity<APIResponse<?>> resendOtp(
            @RequestParam String mailId
    ){
        log.info("{} Processing request to regenerate OTP.",mailId);
        otpService.generateOtp(mailId);
        return ResponseEntity.ok(utility.getSuccessResponse(null, "OTP resent successfully."));
    }

    //generates an OTP to reset password
    @GetMapping("/password")
    public ResponseEntity<APIResponse<?>> generateOtpToRestPassword(
            @RequestParam String id
    ){
        authService.resetPassword(id);
        return ResponseEntity.ok(utility.getSuccessResponse(null, "A 6 digit OTP generated successfully to reset password, the otp is valid for 10 minutes only."));
    }

    //submit the generated otp and new password
    @PostMapping("/submitOtp")
    public ResponseEntity<APIResponse<?>> verifyOtpAndResetPassword(
            @RequestBody @Valid ResetPasswordRequest request
    ){
        authService.verifyAndResetPassword(request);
        return ResponseEntity.ok(utility.getSuccessResponse(null, "Password reset successful, please re-login."));
    }

    @GetMapping("/status")
    public ResponseEntity<APIResponse<?>> trackStatus(
            @RequestParam String bankId
    ){
        StatusCheckResponse response = statusCheckService.checkUserStatus(bankId);
        log.info("{} Status check response returned: {}",bankId, response);
        return ResponseEntity.ok(utility.getSuccessResponse(response, "Status Checked successfully."));
    }

}
