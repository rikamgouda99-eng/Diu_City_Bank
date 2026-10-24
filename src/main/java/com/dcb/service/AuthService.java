package com.dcb.service;

import com.dcb.model.*;

public interface AuthService {
    SignupResponse doSignUp(SignupRequest request);
    void verifyOtp(OtpRequest request);
    LoginResponse doLogin(LoginRequest request);
    void resetPassword(String id);
    void verifyAndResetPassword(ResetPasswordRequest request);
}
