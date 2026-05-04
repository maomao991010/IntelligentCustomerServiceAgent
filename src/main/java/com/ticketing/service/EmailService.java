package com.ticketing.service;

public interface EmailService {
    void sendVerificationCode(String email, String code);
}
