package com.dcb.service;

import com.dcb.entity.UserEntity;
import com.dcb.exception.BankException;
import com.dcb.model.StatusCheckResponse;
import com.dcb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StatusCheckService {

    private final UserRepo userRepo;

    public StatusCheckResponse checkUserStatus(String bankId){
        long start = System.currentTimeMillis();

        if(bankId == null || bankId.isEmpty()){
            log.info("Bank ID is null or empty in the request.");
            throw new BankException("Please enter bank ID in the request.");
        }

       UserEntity user = userRepo.findById(bankId).orElseThrow(()->{
            log.info("{} Invalid request detected, User not exists",bankId);
            return new BankException("User not exists, please try with a valid bank ID.");
       });
       StatusCheckResponse response = StatusCheckResponse.builder()
               .mailId(user.getMailId())
               .bankId(user.getBankId())
               .accountCreationDate(user.getCreatedDate().toString())
               .lastUpdateDate(user.getUpdatedDate().toString())
               .status(user.getStatus())
               .isActive(user.getIsActive())
               .isApproved(user.getIsApproved())
               .isVerified(user.getIsVerified())
               .build();
       log.info("{} Total time taken to process status check for user request is: {} ms",bankId,System.currentTimeMillis() - start);
       return response;
    }

}
