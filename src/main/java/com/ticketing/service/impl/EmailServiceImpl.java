package com.ticketing.service.impl;

import com.ticketing.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Properties;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username:your-email@qq.com}")
    private String fromEmail;

    @Override
    public void sendVerificationCode(String email, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("【票务系统】注册验证码");
            message.setText("您的注册验证码为：" + code + "，5分钟内有效。请勿将验证码泄露给他人。");
            
            javaMailSender.send(message);
            log.info("验证码邮件发送成功: {}", email);
        } catch (Exception e) {
            log.error("验证码邮件发送失败: {}", email, e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
