package com.dcb.util;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.UserEntity;
import com.dcb.model.APIResponse;
import com.dcb.model.LoginResponse;
import com.dcb.model.SignupResponse;
import com.dcb.model.UserResponse;
import com.dcb.repository.UserRepo;
import org.springframework.stereotype.Component;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.Random;

@Component
public class Util{
    public APIResponse<?> getSuccessResponse(Object data, String message){
        APIResponse<Object> response = new APIResponse<>();
        response.setData(data);
        response.setIsSuccess(true);
        response.setMessage(message);
        response.setTimestamp(LocalDate.now().toString());
        return response;
    }
    public APIResponse<Object> getErrorResponse(Object data, String message){
        APIResponse<Object> response = new APIResponse<>();
        response.setData(data);
        response.setIsSuccess(false);
        response.setMessage(message);
        response.setTimestamp(LocalDate.now().toString());
        return response;
    }
    public static SignupResponse getSignupResponse(String mail, String name, String bankId){
        return SignupResponse.builder()
                .mailId(mail)
                .name(name)
                .bankId(bankId)
                .build();
    }
    public static String generateBankId(){
        String bankCode = "DIU";
        SimpleDateFormat format = new SimpleDateFormat("yyMMdd");
        String batch = format.format(new Date());
        String serialNumber = String.valueOf(100000L + new Random().nextLong(900000L));
        return bankCode + batch + serialNumber;
    }
    public static LoginResponse getLoginResponse(String bankId, String accessToken, String refreshToken){
        return LoginResponse.builder()
                .bankId(bankId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
    public static String getAccountNumber(){
        SimpleDateFormat format = new SimpleDateFormat("MMdd");
        String batch = format.format(new Date());
        String serialNumber = String.valueOf(10000000L + new Random().nextLong(90000000L));
        return "3567" + batch + serialNumber;
    }
    public static UserResponse getUserResponse(UserEntity user, BankAccountEntity account){
        return UserResponse.builder()
                .bankID(user.getBankId())
                .mailId(user.getMailId())
                .dob(user.getDob().toString())
                .accountBalance(account.getAccountBalance().toString())
                .accountNumber(account.getAccountNo())
                .accountOpeningDate(user.getCreatedDate().toString())
                .accountUpdateDate(user.getUpdatedDate().toString())
                .address(user.getAddressDetails())
                .gender(user.getGender())
                .name(user.getFirstName() + (user.getMiddleName()==null?"":user.getMiddleName()) + user.getLastName())
                .fixedAccountNumber(account.getAccountNo())
                .loanAccountNumber(null)//need to change
                .maritalStatus(user.getMaritalStatus())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }

}
