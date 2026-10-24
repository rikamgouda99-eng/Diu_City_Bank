package com.dcb.exception;

public class BankException extends RuntimeException{
    private String message;
    public BankException(String message){
        super(message);
        this.message = message;
    }
}
