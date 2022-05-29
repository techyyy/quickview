package com.example.quickview.exception;

public class RoomIsFullException extends Exception {
    public RoomIsFullException(String errorMessage) {
        super(errorMessage);
    }
}
