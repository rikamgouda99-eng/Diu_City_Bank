package com.dcb.util;

import com.dcb.exception.BankException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/** Detects a file's real type from its first bytes instead of trusting the client's Content-Type. */
public final class FileTypes {

    public static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> DOCUMENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    private FileTypes() {
    }

    public static String detect(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return "application/pdf";
        return null;
    }

    /** Reads and validates an upload; returns {detectedContentType, bytes}. */
    public static Upload read(MultipartFile file, Set<String> allowed, long maxBytes, String allowedLabel) {
        if (file == null || file.isEmpty()) {
            throw new BankException("Please select a file to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new BankException("File is too large. Maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BankException("Could not read the uploaded file.");
        }
        String type = detect(bytes);
        if (type == null || !allowed.contains(type)) {
            throw new BankException("Invalid file format. Allowed formats: " + allowedLabel + ".");
        }
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_");
        return new Upload(type, bytes, name.length() > 100 ? name.substring(name.length() - 100) : name);
    }

    public record Upload(String contentType, byte[] bytes, String fileName) {
    }
}
