package com.dcb.service;

import com.dcb.config.BankProperties;
import com.dcb.entity.KycRequestEntity;
import com.dcb.enums.KycDocumentType;
import com.dcb.enums.KycStatus;
import com.dcb.exception.BankException;
import com.dcb.exception.NotFoundException;
import com.dcb.model.DecisionRequest;
import com.dcb.model.KycResponse;
import com.dcb.model.KycStatusResponse;
import com.dcb.model.PageResponse;
import com.dcb.repository.KycRequestRepo;
import com.dcb.repository.UserRepo;
import com.dcb.util.FileTypes;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class KycService {

    private final KycRequestRepo kycRepo;
    private final UserRepo userRepo;
    private final BankProperties props;

    public KycStatus statusOf(String bankId) {
        return kycRepo.findFirstByBankIdOrderBySubmittedAtDesc(bankId).map(KycRequestEntity::getStatus).orElse(KycStatus.NOT_SUBMITTED);
    }

    public KycStatusResponse myKyc(String bankId) {
        List<KycResponse> history = kycRepo.findByBankIdOrderBySubmittedAtDesc(bankId).stream().map(k -> toResponse(k, null)).toList();
        return KycStatusResponse.builder()
                .kycStatus(history.isEmpty() ? KycStatus.NOT_SUBMITTED.name() : history.get(0).getStatus())
                .latest(history.isEmpty() ? null : history.get(0))
                .history(history)
                .build();
    }

    @Transactional
    public KycResponse submit(String bankId, KycDocumentType documentType, String documentNumber, MultipartFile file) {
        if (documentType == null) {
            throw new BankException("Document type is mandatory.");
        }
        KycStatus current = statusOf(bankId);
        if (current == KycStatus.PENDING) {
            throw new BankException("Your KYC is already under review.");
        }
        if (current == KycStatus.APPROVED) {
            throw new BankException("Your KYC is already approved.");
        }
        String number = normaliseNumber(documentType, documentNumber);
        FileTypes.Upload upload = FileTypes.read(file, FileTypes.DOCUMENT_TYPES, props.getUpload().getKycMaxBytes(), "PDF, JPG, JPEG, PNG, WebP");

        KycRequestEntity kyc = kycRepo.save(KycRequestEntity.builder()
                .bankId(bankId)
                .documentType(documentType)
                .documentNumber(number)
                .fileName(upload.fileName())
                .contentType(upload.contentType())
                .documentData(upload.bytes())
                .status(KycStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build());
        log.info("{} submitted KYC {} ({})", bankId, kyc.getId(), documentType);
        return toResponse(kyc, null);
    }

    @Transactional(readOnly = true)
    public KycRequestEntity ownDocument(String bankId, Long id) {
        KycRequestEntity kyc = kycRepo.findByIdAndBankId(id, bankId).orElseThrow(() -> new NotFoundException("KYC request not found."));
        kyc.getDocumentData();
        return kyc;
    }

    public PageResponse<KycResponse> adminList(KycStatus status, int page, int size) {
        return PageResponse.of(kycRepo.search(status, Util.page(page, size)),
                k -> toResponse(k, userRepo.findById(k.getBankId()).map(Util::fullName).orElse(null)));
    }

    @Transactional(readOnly = true)
    public KycRequestEntity adminDocument(Long id) {
        KycRequestEntity kyc = kycRepo.findById(id).orElseThrow(() -> new NotFoundException("KYC request not found."));
        kyc.getDocumentData();
        return kyc;
    }

    @Transactional
    public KycResponse review(Long id, boolean approve, String adminBankId, DecisionRequest request) {
        KycRequestEntity kyc = kycRepo.findById(id).orElseThrow(() -> new NotFoundException("KYC request not found."));
        if (kyc.getStatus() != KycStatus.PENDING) {
            throw new BankException("KYC request is already " + kyc.getStatus().name().toLowerCase() + ".");
        }
        String comment = Util.blankToNull(request == null ? null : request.getComment());
        if (!approve && comment == null) {
            throw new BankException("Please give a reason for rejecting the KYC.");
        }
        kyc.setStatus(approve ? KycStatus.APPROVED : KycStatus.REJECTED);
        kyc.setAdminComment(comment);
        kyc.setReviewedBy(adminBankId);
        kyc.setReviewedAt(LocalDateTime.now());
        kycRepo.save(kyc);
        log.info("KYC {} of {} {} by {}", id, kyc.getBankId(), kyc.getStatus(), adminBankId);
        return toResponse(kyc, userRepo.findById(kyc.getBankId()).map(Util::fullName).orElse(null));
    }

    private String normaliseNumber(KycDocumentType type, String raw) {
        String number = raw == null ? "" : raw.replaceAll("\\s", "").toUpperCase();
        boolean valid = switch (type) {
            case AADHAAR -> number.matches("\\d{12}");
            case PAN -> number.matches("[A-Z]{5}\\d{4}[A-Z]");
            default -> number.matches("[A-Z0-9-]{6,20}");
        };
        if (!valid) {
            throw new BankException(switch (type) {
                case AADHAAR -> "Aadhaar number must be 12 digits.";
                case PAN -> "PAN must look like ABCDE1234F.";
                default -> "Document number must be 6-20 letters or digits.";
            });
        }
        return number;
    }

    private KycResponse toResponse(KycRequestEntity k, String customerName) {
        String number = k.getDocumentNumber();
        return KycResponse.builder()
                .id(k.getId())
                .bankId(k.getBankId())
                .customerName(customerName)
                .documentType(k.getDocumentType().name())
                .documentNumber(number.length() <= 4 ? number : "X".repeat(number.length() - 4) + number.substring(number.length() - 4))
                .fileName(k.getFileName())
                .contentType(k.getContentType())
                .status(k.getStatus().name())
                .adminComment(k.getAdminComment())
                .reviewedBy(k.getReviewedBy())
                .submittedAt(k.getSubmittedAt())
                .reviewedAt(k.getReviewedAt())
                .build();
    }
}
