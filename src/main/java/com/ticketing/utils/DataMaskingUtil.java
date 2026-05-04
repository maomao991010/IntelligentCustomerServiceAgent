package com.ticketing.utils;

import org.springframework.util.StringUtils;

/**
 * 数据脱敏工具类
 * 用于对敏感数据进行脱敏处理，保护用户隐私
 */
public class DataMaskingUtil {

    /**
     * 手机号脱敏
     * 保留前3位和后4位，中间用*代替
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏
     * 保留用户名前2位和域名，中间用*代替
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];
        
        if (username.length() <= 2) {
            return username + "****@" + domain;
        }
        return username.substring(0, 2) + "****@" + domain;
    }

    /**
     * 姓名脱敏
     * 保留姓氏，名字用*代替
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            sb.append("*");
        }
        return sb.toString();
    }

    /**
     * 身份证号脱敏
     * 保留前6位和后4位，中间用*代替
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String maskIdCard(String idCard) {
        if (!StringUtils.hasText(idCard) || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡号脱敏
     * 保留前4位和后4位，中间用*代替
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String maskBankCard(String bankCard) {
        if (!StringUtils.hasText(bankCard) || bankCard.length() < 8) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + " **** **** " + bankCard.substring(bankCard.length() - 4);
    }
}
