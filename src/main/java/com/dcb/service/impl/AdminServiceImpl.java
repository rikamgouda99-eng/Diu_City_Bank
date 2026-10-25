package com.dcb.service.impl;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.UserEntity;
import com.dcb.exception.BankException;
import com.dcb.model.ApproveUserRequest;
import com.dcb.model.UserResponse;
import com.dcb.repository.BankAccountRepo;
import com.dcb.repository.UserRepo;
import com.dcb.service.AdminService;
import com.dcb.service.MailService;
import com.dcb.util.Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepo userRepo;
    private final BankAccountRepo bankRepo;
    private final MailService mailService;
    ExecutorService executorService = Executors.newFixedThreadPool(10);


    @Override
    public UserResponse getUserDetails(String bankId) {
        if(bankId == null || bankId.isEmpty()){
            log.info("Bank ID is missing, session expired.");
            throw new BankException("Session expired, please login again");
        }
        UserEntity user = userRepo.findById(bankId).orElseThrow(()->{
            log.info("{} User not exists, please provide a valid bank ID",bankId);
            return new BankException(bankId+" User not exists, please provide valid bank ID");
        });
        BankAccountEntity account = bankRepo.findByCustomerId(user.getBankId()).orElse(new BankAccountEntity());
        return Util.getUserResponse(user, account);
    }

    @Override
    public List<UserEntity> getPendingApprovals() {
        long start = System.currentTimeMillis();
        List<UserEntity> users = userRepo.getPendingUsers();
        log.info("Total user pending for approvals: {}, the users are: {}",users.size(),users);
        log.info("Total time taken to process get pending user approvals is: {}ms",System.currentTimeMillis() - start);
        return users;
    }

    @Override
    public boolean approvePendingUsers(ApproveUserRequest request) {
        long start = System.currentTimeMillis();
        log.info("{} Processing approve pending user request: ",request);
        boolean isSuccess = false;

        UserEntity user = userRepo.findById(request.getBankId()).orElseThrow(()->{
            log.info("{} User not exists, please provide valid bank ID",request.getBankId());
            return new BankException(request.getBankId()+" User not exists, please provide valid bank ID");
        });

        if(Boolean.FALSE.equals(user.getIsActive())){
            log.info("{} User is not active, please active the user first to approve or generate bank account.",request.getBankId());
            throw new BankException(request.getBankId()+" User not active, please activate the user first to process account opening.");
        }

        if(Boolean.TRUE.equals(user.getIsApproved())){
            log.info("{} Duplicate request, user is already approved.",request.getBankId());
            throw new BankException("Duplicate request, user is already approved");
        }

        if(request.getAction().equalsIgnoreCase("APPROVE")){
            log.info("{} processing application approval request.",request.getBankId());
            user.setIsApproved(true);
            user.setUpdatedDate(LocalDate.now());
            user.setStatus(request.getStatus()!=null?request.getStatus():"APPROVED BY ADMIN");
            userRepo.save(user);
            log.info("{} Approved successfully.",request.getBankId());

            //process bank account creation
            log.info("{} processing bank account creation.",request.getBankId());
            BankAccountEntity bankAccount = new BankAccountEntity();
            bankAccount.setCustomerId(user.getBankId());
            bankAccount.setAccountNo(getBankAccount());
            bankAccount.setAccountBalance(1_000_00.0D);
            bankRepo.save(bankAccount);
            log.info("{} Bank account created successfully, account number: {}",bankAccount.getCustomerId(), bankAccount.getAccountNo());

            //send mail to account holder
            log.info("{} sending bank account opening confirmation mail to account holder: ",user.getBankId());
            executorService.submit(()->mailService.sendAccountOpenMail(user.getMailId(), user.getFirstName(), bankAccount.getAccountNo()));
            isSuccess = true;
        } else {
            log.info("{} processing application reject request.",request.getBankId());
            user.setIsApproved(false);
            user.setIsActive(false);
            user.setUpdatedDate(LocalDate.now());
            user.setStatus(request.getStatus()!=null?request.getStatus():"REJECTED BY ADMIN");
            userRepo.save(user);
            log.info("{} Rejected successfully.",request.getBankId());

            log.info("{} sending bank account opening request reject confirmation mail to account holder: ",user.getBankId());
            executorService.submit(()->mailService.sendAccountOpeningRejectedMail(user.getMailId(), user.getFirstName(), user.getStatus()));
        }
        log.info("{} Completed approve pending request.",request.getBankId());
        log.info("Total time taken to process user approval & account creation is : {}ms",System.currentTimeMillis() - start);
        return isSuccess;
    }

    @Override
    public void deActivateUser(String bankId) {
        long start = System.currentTimeMillis();
        if(bankId == null || bankId.isEmpty()){
            log.info("Bank ID is missing, please enter the bank id");
            throw new BankException("Bank ID is missing, please enter the bank id");
        }
        UserEntity user = userRepo.findById(bankId).orElseThrow(()->{
            log.info("{} User not exists, please provide a valid bank ID",bankId);
            return new BankException(bankId+" User not exists, please provide valid bank ID");
        });
        user.setIsActive(false);
        user.setUpdatedDate(LocalDate.now());
        userRepo.save(user);
        log.info("Total time taken to process de-activate user is : {}ms",System.currentTimeMillis() - start);
    }

    @Override
    public void activateUser(String bankId) {
        long start = System.currentTimeMillis();
        if(bankId == null || bankId.isEmpty()){
            log.info("Bank ID is missing, please enter the bank id");
            throw new BankException("Bank ID is missing, please enter the bank id");
        }
        UserEntity user = userRepo.findById(bankId).orElseThrow(()->{
            log.info("{} User not exists, please provide a valid bank ID",bankId);
            return new BankException(bankId+" User not exists, please provide valid bank ID");
        });
        user.setIsActive(true);
        user.setUpdatedDate(LocalDate.now());
        userRepo.save(user);
        log.info("Total time taken to process activate user is : {}ms",System.currentTimeMillis() - start);
    }

    private String getBankAccount(){
        String accountNumber = Util.getAccountNumber();

        if(bankRepo.existsById(accountNumber)){
            return getBankAccount();
        }
        return accountNumber;
    }
}
