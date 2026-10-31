package com.dcb.util;

import com.dcb.entity.BankAccountEntity;
import com.dcb.entity.UserEntity;
import com.dcb.model.APIResponse;
import com.dcb.model.LoginResponse;
import com.dcb.model.SignupResponse;
import com.dcb.model.UserResponse;
import com.dcb.repository.UserRepo;
import jakarta.validation.constraints.Null;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
                .accountBalance(account.getAccountBalance()!= null? account.getAccountBalance().toPlainString(): "")
                .accountNumber(account.getAccountNo()!= null? account.getAccountNo():"")
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // e.g. TXN2610301845221234567 - prefix + yyMMddHHmmss + 7 random digits
    public static String generateReference(String prefix){
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        return prefix + time + String.format("%07d", SECURE_RANDOM.nextInt(10_000_000));
    }

    // 12 digit retrieval reference number: yDDD + HH + 6 random digits
    public static String generateRrn(){
        LocalDateTime now = LocalDateTime.now();
        return String.valueOf(now.getYear() % 10)
                + String.format("%03d", now.getDayOfYear())
                + String.format("%02d", now.getHour())
                + String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    // 6 digit system trace audit number
    public static long generateStan(){
        return 100000L + SECURE_RANDOM.nextInt(900000);
    }

    public static String randomDigits(int length){
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    // 3567102737438112 -> XXXXXXXXXXXX8112
    public static String maskAccount(String accountNo){
        if (accountNo == null || accountNo.length() <= 4) return accountNo;
        return "X".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
    }

    // "Rikam Gouda" -> "R**** G****"
    public static String maskName(String name){
        if (name == null || name.isBlank()) return name;
        StringBuilder sb = new StringBuilder();
        for (String part : name.trim().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(part.charAt(0)).append("*".repeat(Math.max(part.length() - 1, 1)));
        }
        return sb.toString();
    }

    public static String fullName(UserEntity user){
        if (user == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{user.getFirstName(), user.getMiddleName(), user.getLastName()}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(part.trim());
            }
        }
        return sb.toString();
    }

    public static Pageable page(int page, int size){
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    public static String blankToNull(String value){
        return value == null || value.isBlank() ? null : value.trim();
    }

}
