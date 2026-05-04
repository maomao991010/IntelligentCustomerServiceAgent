package com.ticketing.agent.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ArtistAliasResolver {

    @Autowired(required = false)
    private AiModelService aiModelService;

    private static final Map<String, List<String>> ARTIST_ALIASES = new HashMap<>();

    static {
        ARTIST_ALIASES.put("周杰伦", Arrays.asList("jay", "jay chou", "周董", "杰伦"));
        ARTIST_ALIASES.put("林俊杰", Arrays.asList("jj", "jj lin", "俊傑"));
        ARTIST_ALIASES.put("五月天", Arrays.asList("mayday"));
        ARTIST_ALIASES.put("邓紫棋", Arrays.asList("g.e.m.", "gem", "紫棋"));
        ARTIST_ALIASES.put("薛之谦", Arrays.asList("joker", "之谦"));
        ARTIST_ALIASES.put("陈奕迅", Arrays.asList("eason", "eason chan", "奕迅"));
        ARTIST_ALIASES.put("张学友", Arrays.asList("jacky", "jacky cheung", "学友"));
        ARTIST_ALIASES.put("刘德华", Arrays.asList("andy", "andy lau", "德华"));
        ARTIST_ALIASES.put("王力宏", Arrays.asList("leehom", "leehom wang", "力宏"));
        ARTIST_ALIASES.put("蔡依林", Arrays.asList("jolin", "jolin tsai", "依林"));
        ARTIST_ALIASES.put("张杰", Arrays.asList());
    }

    public String resolveArtistName(String question, String extractedKeyword) {
        log.info("开始解析艺人别名: question={}, keyword={}", question, extractedKeyword);

        String keyword = extractedKeyword != null ? extractedKeyword.trim() : "";

        if (keyword.isEmpty()) {
            return extractedKeyword;
        }

        if (isRealName(keyword)) {
            log.info("输入已是艺人本名，无需解析: {}", keyword);
            return keyword;
        }

        boolean hasKnownAlias = hasKnownAlias(keyword);
        if (!hasKnownAlias) {
            log.info("未识别到已知别名，直接使用原始关键词: {}", keyword);
            return keyword;
        }

        String matched = matchByAlias(keyword);
        if (matched != null) {
            log.info("通过本地别名匹配成功: {} -> {}", keyword, matched);
            return matched;
        }

        if (aiModelService != null && aiModelService.isAvailable()) {
            String resolved = resolveByLLM(question);
            if (resolved != null && !resolved.trim().isEmpty()) {
                log.info("通过LLM解析成功: {} -> {}", keyword, resolved);
                return resolved;
            }
        }

        log.info("未找到别名，使用原始关键词: {}", extractedKeyword);
        return extractedKeyword;
    }

    private boolean isRealName(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        for (String realName : ARTIST_ALIASES.keySet()) {
            if (realName.toLowerCase().equals(lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKnownAlias(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        for (List<String> aliases : ARTIST_ALIASES.values()) {
            for (String alias : aliases) {
                if (alias.toLowerCase().equals(lowerKeyword) || lowerKeyword.contains(alias.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String matchByAlias(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        String lowerKeyword = keyword.toLowerCase().trim();

        for (Map.Entry<String, List<String>> entry : ARTIST_ALIASES.entrySet()) {
            String realName = entry.getKey();
            
            if (realName.toLowerCase().contains(lowerKeyword) || lowerKeyword.contains(realName.toLowerCase())) {
                return realName;
            }

            for (String alias : entry.getValue()) {
                if (alias.toLowerCase().equals(lowerKeyword) || lowerKeyword.contains(alias.toLowerCase())) {
                    return realName;
                }
            }
        }

        return null;
    }

    private String resolveByLLM(String question) {
        try {
            String prompt = buildAliasPrompt(question);
            String response = aiModelService.generateAnswer(question, "", prompt);
            return parseArtistName(response);
        } catch (Exception e) {
            log.warn("LLM别名解析失败", e);
            return null;
        }
    }

    private String buildAliasPrompt(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个艺人名称识别助手。请从用户的问题中提取出真实的艺人全名。\n\n");
        sb.append("常见艺人别名对照表：\n");
        sb.append("- jay, 周董, 杰伦 -> 周杰伦\n");
        sb.append("- jj, 俊傑 -> 林俊杰\n");
        sb.append("- mayday -> 五月天\n");
        sb.append("- g.e.m., gem, 紫棋 -> 邓紫棋\n");
        sb.append("- joker, 之谦 -> 薛之谦\n");
        sb.append("- eason, 奕迅 -> 陈奕迅\n");
        sb.append("- jacky, 学友 -> 张学友\n");
        sb.append("- andy, 德华 -> 刘德华\n");
        sb.append("- leehom, 力宏 -> 王力宏\n");
        sb.append("- jolin, 依林 -> 蔡依林\n\n");
        sb.append("用户问题：").append(question).append("\n\n");
        sb.append("请只返回艺人的真实中文全名（如：周杰伦），不要返回其他内容。如果不确定，返回空字符串。");
        return sb.toString();
    }

    private String parseArtistName(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }

        String clean = response.trim();
        
        if (clean.equals("") || clean.equals("空") || clean.equals("null")) {
            return null;
        }

        for (String realName : ARTIST_ALIASES.keySet()) {
            if (clean.contains(realName)) {
                return realName;
            }
        }

        return clean;
    }
}
