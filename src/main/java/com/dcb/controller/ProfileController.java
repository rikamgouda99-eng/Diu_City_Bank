package com.dcb.controller;

import com.dcb.entity.ProfilePhotoEntity;
import com.dcb.model.*;
import com.dcb.service.ProfileService;
import com.dcb.util.Util;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Profile endpoints for both customers and admins. Always acts on the logged-in user. */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final Util util;

    @GetMapping
    public ResponseEntity<APIResponse<?>> get(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(profileService.get(user.getUsername()), "Profile fetched."));
    }

    @PutMapping
    public ResponseEntity<APIResponse<?>> update(@AuthenticationPrincipal UserDetails user,
                                                 @RequestBody @Valid ProfileUpdateRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(profileService.update(user.getUsername(), request), "Profile updated."));
    }

    @PostMapping("/email")
    public ResponseEntity<APIResponse<?>> requestEmailChange(@AuthenticationPrincipal UserDetails user,
                                                             @RequestBody @Valid EmailChangeRequest request) {
        profileService.requestEmailChange(user.getUsername(), request);
        return ResponseEntity.ok(util.getSuccessResponse(null, "A 6 digit OTP was sent to the new email, it is valid for 10 minutes."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<APIResponse<?>> verifyEmailChange(@AuthenticationPrincipal UserDetails user,
                                                            @RequestBody @Valid EmailVerifyRequest request) {
        return ResponseEntity.ok(util.getSuccessResponse(profileService.verifyEmailChange(user.getUsername(), request), "Email updated."));
    }

    @PutMapping("/password")
    public ResponseEntity<APIResponse<?>> changePassword(@AuthenticationPrincipal UserDetails user,
                                                         @RequestBody @Valid ChangePasswordRequest request) {
        profileService.changePassword(user.getUsername(), request);
        return ResponseEntity.ok(util.getSuccessResponse(null, "Password changed successfully."));
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<APIResponse<?>> uploadPhoto(@AuthenticationPrincipal UserDetails user,
                                                      @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(util.getSuccessResponse(profileService.uploadPhoto(user.getUsername(), file), "Profile photo saved."));
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(@AuthenticationPrincipal UserDetails user) {
        ProfilePhotoEntity photo = profileService.photo(user.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(photo.getData());
    }

    @DeleteMapping("/photo")
    public ResponseEntity<APIResponse<?>> deletePhoto(@AuthenticationPrincipal UserDetails user) {
        profileService.deletePhoto(user.getUsername());
        return ResponseEntity.ok(util.getSuccessResponse(null, "Profile photo removed."));
    }
}
