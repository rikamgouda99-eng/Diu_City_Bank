package com.dcb.util;

import com.dcb.entity.UserEntity;
import com.dcb.model.APIResponse;
import com.dcb.model.LoginResponse;
import com.dcb.model.SignupResponse;
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

}
