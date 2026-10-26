package com.dcb.service.impl;

import com.dcb.entity.OtpEntity;
import com.dcb.model.*;
import com.dcb.repository.OtpRepo;
import com.dcb.service.CustomUserDetailsService;
import com.dcb.service.JwtService;
import com.dcb.util.Util;
import com.dcb.entity.UserEntity;
import com.dcb.exception.BankException;
import com.dcb.repository.UserRepo;
import com.dcb.service.AuthService;
import com.dcb.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepo userRepo;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final OtpRepo otpRepo;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    @Override
    public SignupResponse doSignUp(SignupRequest request) {
        long start = System.currentTimeMillis();
        request.setPassword(passwordEncoder.encode(request.getPassword()));
        log.info("{} processing signup request: {}",request.getMailId(), request);

        //check mail id idempotency
        if(userRepo.existsByMailId(request.getMailId())){
            log.info("{} Mail ID already exists, please try with another mail ID.",request.getMailId());
            throw new BankException("Mail ID already exists, please try with unique mail ID");
        }

        //create User Entity
        UserEntity userEntity = new UserEntity();
        String bankId = getBankId();

        userEntity.setBankId(bankId);
        userEntity.setMailId(request.getMailId());
        userEntity.setFirstName(request.getFirstName());
        userEntity.setMiddleName(request.getMiddleName());
        userEntity.setLastName(request.getLastName());
        userEntity.setDob(request.getDob());
        userEntity.setGender(request.getGender());
        userEntity.setAddressDetails(request.getAddressDetails());
        userEntity.setCreatedDate(LocalDate.now());
        userEntity.setMaritalStatus(request.getMaritalStatus());
        userEntity.setPhoneNumber(request.getPhoneNumber());
        userEntity.setRole("CUSTOMER");
        userEntity.setPassword(request.getPassword());
        userEntity.setStatus("Initiated by customer");
        userEntity.setIsActive(true);
        userEntity.setIsVerified(false);
        userEntity.setIsApproved(false);

        //save record in DB
        userEntity = userRepo.save(userEntity);
        log.info("{} User record inserted successfully: {}",userEntity.getBankId(), userEntity);

        //generate OTP
        otpService.generateOtp(request.getMailId());

        SignupResponse response = Util.getSignupResponse(userEntity.getMailId(), userEntity.getFirstName(), userEntity.getBankId());
        log.info("{} response returned to application: {}",request.getMailId(), response);
        log.info("{} Total time taken to process generate OTP request is: {} ms",request.getMailId(),System.currentTimeMillis() - start);
        return response;
    }

    @Override
    public void verifyOtp(OtpRequest request) {
        long start = System.currentTimeMillis();
        log.info("{} Verify otp request received: {}",request.getMailId(), request);

        UserEntity userEntity = userRepo.findByMailId(request.getMailId()).orElseThrow(()->{
            log.info("{} Invalid Mail ID provided.",request.getMailId());
            return new BankException("Please provide a valid mail ID");
        });

        if(Boolean.TRUE.equals(userEntity.getIsVerified()) || Boolean.TRUE.equals(userEntity.getIsApproved())){
            log.info("{} user is already verified, duplicate request detected.",userEntity.getMailId());
            throw new BankException("Already verified, please login using your credentials.");
        }

        if(Boolean.FALSE.equals(userEntity.getIsActive())){
            log.info("{} user is not active, please contact to admin.",userEntity.getMailId());
            throw new BankException("Something went wrong, please contact our support team.");
        }

        OtpEntity otp = otpRepo.findById(request.getMailId()).orElseThrow(()->{
            log.info("{} Invalid mail ID entered, please try with the original one.", request.getMailId());
            return new BankException("Invalid mail ID entered, please try with the original one.");
        });

        if(otp.getExpiry().isBefore(LocalDateTime.now())){
            log.info("{} The OTP is expired, please consider generating a new OTP.",request.getOtp());
            throw new BankException("OTP is expired.");
        }

        if(!passwordEncoder.matches(request.getOtp(), otp.getOtp())){
            log.info("{} OTP mismatch, please provide the correct OTP",request.getOtp());
            throw new BankException("OTP mismatch, please enter the correct 6 digit OTP.");
        }

        //update user record
        userEntity.setIsVerified(true);
        userEntity.setStatus("User is verified");
        userEntity.setUpdatedDate(LocalDate.now());
        userRepo.save(userEntity);

        //delete the otp record
        otpRepo.delete(otp);
        log.info("{} OTP is verified successfully",request.getMailId());
        log.info("{} Total time taken to process verify OTP request is: {} ms",request.getMailId(),System.currentTimeMillis() - start);
    }

    @Override
    public LoginResponse doLogin(LoginRequest request) {
        long start = System.currentTimeMillis();
        log.info("{} Processing Login request: {}",request.getBankId(), request);

        UserEntity user;
        //Process Login
        if(request.getMailId()==null || request.getMailId().isEmpty()){
            // Bank ID login
            if(request.getBankId() == null || request.getBankId().isEmpty()){
                log.info("Invalid request detected, both mail ID & bank ID can't be null at least one is required.");
                throw new BankException("Please enter mail ID or Bank ID for login");
            }

            user = userRepo.findById(request.getBankId()).orElseThrow(()->{
                log.info("{} Invalid request detected, User not exists",request.getBankId());
                return new BankException("User not exists, please try with a valid request.");
            });
            log.info("{} User fetched from DB using BankID is: {}",user.getBankId(), user.getFirstName());
        }else{
            //mail ID login
            user = userRepo.findByMailId(request.getMailId()).orElseThrow(()->{
                log.info("{} Invalid request detected,User not exists",request.getMailId());
                return new BankException("User not exists, please try with a valid request.");
            });
            log.info("{} User fetched from DB using mailID is: {}",user.getMailId(), user.getFirstName());
        }

        //validate password
        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            log.info("{} Invalid password entered.",user.getBankId());
            throw new BankException("Wrong password entered!");
        }

        //login successful
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getBankId());
        String accessToken = jwtService.generateToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        log.info("{} User Login successful",request.getBankId());
        log.info("{} Total time taken to process Login request is: {} ms",request.getMailId(),System.currentTimeMillis() - start);
        return Util.getLoginResponse(user.getBankId(), accessToken, refreshToken);
    }

    @Override
    public void resetPassword(String id) {
        long start = System.currentTimeMillis();
        if(id == null || id.isEmpty()){
            log.info("Mail ID or Bank ID is required to reset password.");
            throw new BankException("Mail ID or Bank ID is required to reset password.");
        }
        UserEntity user;
        //Bank ID check
        Optional<UserEntity> userEntityOptional = userRepo.findById(id);
        if(userEntityOptional.isEmpty()){
            //Mail ID check
            userEntityOptional = userRepo.findByMailId(id);
            if(userEntityOptional.isEmpty()){
                log.info("{} User not exists in DB",id);
                throw new BankException("User not exists, please enter a valid mailId or Bank ID");
            }
            //from Mail ID
            user = userEntityOptional.get();
        }else{
            //from Bank ID
            user = userEntityOptional.get();
        }
        log.info("{} User fetched from DB successfully",user.getMailId());

        //generate OTP for reset password
        otpService.generateOtp(user.getMailId());

        log.info("{} Total time taken to process reset password is: {} ms",user.getBankId(),System.currentTimeMillis() - start);

    }

    @Override
    public void verifyAndResetPassword(ResetPasswordRequest request) {
        log.info("Incoming password reset requst={}",request);
        long start = System.currentTimeMillis();

        UserEntity userEntity = userRepo.findByMailId(request.getMailId()).orElseThrow(()->{
            log.info("{} Invalid Mail Id provided.",request.getMailId());
            return new BankException("Please provide a valid mail ID");
        });

        if(Boolean.FALSE.equals(userEntity.getIsActive())){
            log.info("{} user is not active, please contact to admin.",userEntity.getMailId());
            throw new BankException("Something went wrong, please contact our support team.");
        }

        OtpEntity otp = otpRepo.findById(request.getMailId()).orElseThrow(()->{
            log.info("{} Invalid mail ID entered, please try with the original one.", request.getMailId());
            return new BankException("Invalid mail ID entered, please try with the original one.");
        });

        if(otp.getExpiry().isBefore(LocalDateTime.now())){
            log.info("{} The OTP is expired, please consider generating a new OTP.",request.getOtp());
            throw new BankException("OTP is expired.");
        }

        if(!passwordEncoder.matches(request.getOtp(), otp.getOtp())){
            log.info("{} OTP mismatch, please provide the correct OTP",request.getOtp());
            throw new BankException("OTP mismatch, please enter the correct 6 digit OTP.");
        }

        //delete the otp record
        otpRepo.delete(otp);

        //reset password
        userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
        userEntity.setUpdatedDate(LocalDate.now());
        userRepo.save(userEntity);

        log.info("{} Password changed successfully.", userEntity.getBankId());
        log.info("{} Total time taken to process verify OTP & reset password request is: {} ms",request.getMailId(),System.currentTimeMillis() - start);
    }

    private String getBankId(){
        String bankId = Util.generateBankId();

        if(userRepo.existsByBankId(bankId)){
            return getBankId();
        }
        return bankId;
    }
}
