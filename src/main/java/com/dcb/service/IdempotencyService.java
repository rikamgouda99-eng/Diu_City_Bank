package com.dcb.service;

import com.dcb.entity.IdempotencyRecordEntity;
import com.dcb.exception.BankException;
import com.dcb.repository.IdempotencyRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Idempotency for financial requests. Callers check after taking the account/card lock,
 * so two concurrent requests with the same key are serialised and the second one sees the first's record.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final IdempotencyRepo repo;

    public void validateKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new BankException("Idempotency-Key header must be 8-100 letters, digits, '-' or '_'.");
        }
    }

    /** ID of the result produced by an earlier request with this key, if any. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> findResult(String bankId, String key, String operation) {
        validateKey(key);
        return repo.findById(bankId + ":" + key).map(record -> {
            if (!record.getOperation().equals(operation)) {
                throw new BankException("This Idempotency-Key was already used for a different operation.");
            }
            log.info("{} duplicate {} request detected for key {}, replaying result {}", bankId, operation, key, record.getResultId());
            return record.getResultId();
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void remember(String bankId, String key, String operation, String resultId) {
        repo.save(IdempotencyRecordEntity.builder()
                .id(bankId + ":" + key)
                .operation(operation)
                .resultId(resultId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
