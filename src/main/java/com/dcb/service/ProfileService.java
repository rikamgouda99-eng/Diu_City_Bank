package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.OtpEntity;
import com.dcb.entity.ProfilePhotoEntity;
import com.dcb.entity.UserEntity;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.*;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.OtpRepo;
import com.dcb.repository.ProfilePhotoRepo;
import com.dcb.repository.UserRepo;
import com.dcb.util.FileTypes;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Profile view/update, verified email change, password change and the profile photo. */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepo userRepo;
    private final BankAccountRepo accountRepo;
    private final ProfilePhotoRepo photoRepo;
    private final OtpRepo otpRepo;
    private final OtpService otpService;
    private final KycService kycService;
    private final PasswordEncoder passwordEncoder;
    private final BankProperties props;

    public ProfileResponse get(String bankId) {
        UserEntity user = user(bankId);
        BankAccountEntity account = accountRepo.findByCustomerId(bankId).orElse(null);
        return ProfileResponse.builder()
                .bankId(user.getBankId())
                .customerId(user.getBankId())
                .firstName(user.getFirstName())
                .middleName(user.getMiddleName())
                .lastName(user.getLastName())
                .fullName(Util.fullName(user))
                .mailId(user.getMailId())
                .phoneNumber(user.getPhoneNumber())
                .gender(user.getGender())
                .dob(user.getDob())
                .maritalStatus(user.getMaritalStatus())
                .addressDetails(user.getAddressDetails())
                .role(user.getRole())
                .createdDate(user.getCreatedDate())
                .updatedDate(user.getUpdatedDate())
                .isVerified(user.getIsVerified())
                .isApproved(user.getIsApproved())
                .isActive(user.getIsActive())
                .applicationStatus(user.getStatus())
                .kycStatus("ADMIN".equals(user.getRole()) ? null : kycService.statusOf(bankId).name())
                .hasPhoto(photoRepo.existsById(bankId))
                .accountNo(account == null ? null : account.getAccountNo())
                .accountType(account == null ? null : account.getAccountType() == null ? "SAVINGS" : account.getAccountType())
                .accountStatus(account == null ? null : account.effectiveStatus().name())
                .accountBalance(account == null ? null : account.getAccountBalance())
                .build();
    }

    /** Only personal details change here; IDs, role, balance and verification flags are never touched. */
    @Transactional
    public ProfileResponse update(String bankId, ProfileUpdateRequest request) {
        UserEntity user = user(bankId);
        user.setFirstName(request.getFirstName().trim());
        user.setMiddleName(Util.blankToNull(request.getMiddleName()));
        user.setLastName(request.getLastName().trim());
        user.setPhoneNumber(request.getPhoneNumber().trim());
        user.setAddressDetails(request.getAddressDetails().trim());
        user.setGender(Util.blankToNull(request.getGender()));
        user.setMaritalStatus(request.getMaritalStatus().trim());
        user.setUpdatedDate(LocalDate.now());
        userRepo.save(user);
        log.info("{} profile updated", bankId);
        return get(bankId);
    }

    /** Step 1 of an email change: sends an OTP to the new address using the existing OTP service. */
    public void requestEmailChange(String bankId, EmailChangeRequest request) {
        UserEntity user = user(bankId);
        String newEmail = request.getNewEmail().trim().toLowerCase();
        if (newEmail.equalsIgnoreCase(user.getMailId())) {
            throw new BankException("New email is the same as the current email.");
        }
        if (userRepo.existsByMailId(newEmail)) {
            throw new BankException("This email is already registered with another account.");
        }
        otpService.generateOtp(newEmail);
        log.info("{} requested email change, OTP sent to the new address", bankId);
    }

    /** Step 2: the OTP sent to the new address proves the customer owns it. */
    @Transactional
    public ProfileResponse verifyEmailChange(String bankId, EmailVerifyRequest request) {
        UserEntity user = user(bankId);
        String newEmail = request.getNewEmail().trim().toLowerCase();
        OtpEntity otp = otpRepo.findById(newEmail)
                .orElseThrow(() -> new BankException("No OTP was requested for this email, please request a new one."));
        if (otp.getExpiry().isBefore(LocalDateTime.now())) {
            throw new BankException("OTP is expired.");
        }
        if (!passwordEncoder.matches(request.getOtp(), otp.getOtp())) {
            throw new BankException("OTP mismatch, please enter the correct 6 digit OTP.");
        }
        if (userRepo.existsByMailId(newEmail)) {
            throw new BankException("This email is already registered with another account.");
        }
        otpRepo.delete(otp);
        user.setMailId(newEmail);
        user.setUpdatedDate(LocalDate.now());
        userRepo.save(user);
        log.info("{} email changed after OTP verification", bankId);
        return get(bankId);
    }

    @Transactional
    public void changePassword(String bankId, ChangePasswordRequest request) {
        UserEntity user = user(bankId);
        if (user.getPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BankException("Current password is incorrect.");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BankException("New password must be different from the current password.");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedDate(LocalDate.now());
        userRepo.save(user);
        log.info("{} password changed", bankId);
    }

    @Transactional
    public ProfileResponse uploadPhoto(String bankId, MultipartFile file) {
        user(bankId);
        FileTypes.Upload upload = FileTypes.read(file, FileTypes.IMAGE_TYPES, props.getUpload().getPhotoMaxBytes(), "JPG, JPEG, PNG, WebP");
        ProfilePhotoEntity photo = photoRepo.findById(bankId).orElse(ProfilePhotoEntity.builder().bankId(bankId).build());
        photo.setContentType(upload.contentType());
        photo.setFileName(upload.fileName());
        photo.setSizeBytes((long) upload.bytes().length);
        photo.setData(upload.bytes());
        photo.setUploadedAt(LocalDateTime.now());
        photoRepo.save(photo);
        log.info("{} profile photo saved ({} bytes)", bankId, upload.bytes().length);
        return get(bankId);
    }

    @Transactional(readOnly = true)
    public ProfilePhotoEntity photo(String bankId) {
        ProfilePhotoEntity photo = photoRepo.findById(bankId).orElseThrow(() -> new NotFoundException("No profile photo uploaded."));
        photo.getData(); // load the lazy blob inside the transaction
        return photo;
    }

    @Transactional
    public void deletePhoto(String bankId) {
        if (!photoRepo.existsById(bankId)) {
            throw new NotFoundException("No profile photo uploaded.");
        }
        photoRepo.deleteById(bankId);
    }

    private UserEntity user(String bankId) {
        return userRepo.findById(bankId).orElseThrow(() -> new NotFoundException("User not found."));
    }
}
