package com.dcb.controller;

import com.dcb.entity.KycRequestEntity;
import com.dcb.enums.KycDocumentType;
import com.dcb.model.APIResponse;
import com.dcb.service.KycService;
import com.dcb.service.NotificationService;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;
    private final NotificationService notifier;
    private final Util util;

    @GetMapping
    public ResponseEntity<APIResponse<?>> myKyc(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(util.getSuccessResponse(kycService.myKyc(user.getUsername()), "KYC status fetched."));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<APIResponse<?>> submit(@AuthenticationPrincipal UserDetails user,
                                                 @RequestParam KycDocumentType documentType,
                                                 @RequestParam String documentNumber,
                                                 @RequestParam("file") MultipartFile file) {
        var response = kycService.submit(user.getUsername(), documentType, documentNumber, file);
        notifier.notify(user.getUsername(), "KYC submitted", "We received your KYC document. The bank will review it shortly.",
                NotificationService.details().add("Document", response.getDocumentType()).add("Status", response.getStatus()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(util.getSuccessResponse(response, "KYC submitted for review."));
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<byte[]> document(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        return document(kycService.ownDocument(user.getUsername(), id));
    }

    static ResponseEntity<byte[]> document(KycRequestEntity kyc) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(kyc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(kyc.getFileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(kyc.getDocumentData());
    }
}
