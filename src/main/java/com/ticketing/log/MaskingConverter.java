package com.ticketing.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingConverter extends MessageConverter {

    private static final List<PatternMask> PATTERNS = new ArrayList<>();

    static {
        PATTERNS.add(new PatternMask(
                Pattern.compile("(phone|mobile|手机号?|电话号?)[\"']?\\s*[:=]\\s*[\"']?(1[3-9]\\d)\\d{4}(\\d{4})"),
                "$1****$2"
        ));
        PATTERNS.add(new PatternMask(
                Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})"),
                "$1****$2"
        ));
        PATTERNS.add(new PatternMask(
                Pattern.compile("(email|邮箱|邮件)[\"']?\\s*[:=]\\s*[\"']?([\\w.]{2})[\\w.]*(@[\\w.]+)"),
                "$1****$3"
        ));
        PATTERNS.add(new PatternMask(
                Pattern.compile("(password|passwd|pwd|密码|secret|token|apiKey|api_key|accessKey|access_key|secretKey|secret_key)[\"']?\\s*[:=]\\s*[\"']?\\S+"),
                "$1=******"
        ));
        PATTERNS.add(new PatternMask(
                Pattern.compile("(idCard|id_card|身份证)[\"']?\\s*[:=]\\s*[\"']?(\\d{6})\\d{8}(\\d{4})"),
                "$1********$2"
        ));
        PATTERNS.add(new PatternMask(
                Pattern.compile("(bankCard|bank_card|银行卡)[\"']?\\s*[:=]\\s*[\"']?(\\d{4})\\d{8,11}(\\d{4})"),
                "$1************$2"
        ));
    }

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isEmpty()) {
            return message;
        }
        return mask(message);
    }

    private String mask(String message) {
        String result = message;
        for (PatternMask pm : PATTERNS) {
            Matcher matcher = pm.pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceAll(pm.replacement);
            }
        }
        return result;
    }

    private static class PatternMask {
        final Pattern pattern;
        final String replacement;

        PatternMask(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }
}
