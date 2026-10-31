package com.dcb.exception;

/** Resource missing or not owned by the caller. Both cases return 404 so IDs can't be probed. */
public class NotFoundException extends BankException {
    public NotFoundException(String message) {
        super(message);
    }
}
