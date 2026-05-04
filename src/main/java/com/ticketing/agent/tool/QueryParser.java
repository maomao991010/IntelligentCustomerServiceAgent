package com.ticketing.agent.tool;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class QueryParser {

    private static final List<String> LOCATION_KEYWORDS = Arrays.asList(
        "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", "武汉", "南京", "西安",
        "天津", "苏州", "长沙", "郑州", "青岛", "宁波", "东莞", "佛山", "沈阳", "大连",
        "厦门", "昆明", "合肥", "济南", "福州", "哈尔滨", "长春", "石家庄", "太原", "南昌",
        "南宁", "贵阳", "海口", "兰州", "银川", "西宁", "乌鲁木齐", "呼和浩特", "拉萨", "台北",
        "香港", "澳门", "场馆", "地点", "哪里", "哪个城市", "在哪"
    );

    private static final List<String> ARTIST_INDICATORS = Arrays.asList(
        "的演唱会", "的演出", "的票", "有哪些", "有什么", "有没有", "最近"
    );

    private static final List<String> STOP_WORDS = Arrays.asList(
        "演唱会", "演出", "票", "的", "有", "哪些", "什么", "有没有", "最近", "请问", "我想知道",
        "哪里", "哪个", "吗", "呢", "啊", "呀", "吧", "啦", "在", "有"
    );

    @Data
    public static class QueryResult {
        private String artist;
        private String location;
        private String generalKeyword;
        private QueryType type;
        
        public static QueryResult artist(String artist) {
            QueryResult result = new QueryResult();
            result.setArtist(artist);
            result.setType(QueryType.ARTIST);
            return result;
        }
        
        public static QueryResult location(String location) {
            QueryResult result = new QueryResult();
            result.setLocation(location);
            result.setType(QueryType.LOCATION);
            return result;
        }
        
        public static QueryResult artistAndLocation(String artist, String location) {
            QueryResult result = new QueryResult();
            result.setArtist(artist);
            result.setLocation(location);
            result.setType(QueryType.ARTIST_AND_LOCATION);
            return result;
        }
        
        public static QueryResult general(String keyword) {
            QueryResult result = new QueryResult();
            result.setGeneralKeyword(keyword);
            result.setType(QueryType.GENERAL);
            return result;
        }

        public String getKeyword() {
            if (type == QueryType.ARTIST_AND_LOCATION) {
                return artist + " " + location;
            } else if (type == QueryType.ARTIST) {
                return artist;
            } else if (type == QueryType.LOCATION) {
                return location;
            } else {
                return generalKeyword;
            }
        }
    }

    public enum QueryType {
        ARTIST,
        LOCATION,
        ARTIST_AND_LOCATION,
        GENERAL
    }

    public QueryResult parse(String question) {
        log.info("开始解析查询: {}", question);
        
        String q = question.trim();
        
        String artist = extractArtist(q);
        String location = extractLocation(q);
        
        if (artist != null && !artist.isEmpty() && location != null && !location.isEmpty()) {
            log.info("识别为艺人+地点查询: artist={}, location={}", artist, location);
            return QueryResult.artistAndLocation(artist, location);
        }
        
        if (location != null && !location.isEmpty()) {
            log.info("识别为地点查询: location={}", location);
            return QueryResult.location(location);
        }
        
        if (artist != null && !artist.isEmpty()) {
            log.info("识别为艺人查询: artist={}", artist);
            return QueryResult.artist(artist);
        }
        
        log.info("识别为通用查询: keyword={}", q);
        return QueryResult.general(q);
    }

    private String extractLocation(String q) {
        String result = q;
        
        for (String indicator : ARTIST_INDICATORS) {
            result = result.replace(indicator, "");
        }
        
        for (String stop : STOP_WORDS) {
            result = result.replace(stop, "");
        }
        
        result = result.trim();
        
        List<String> foundLocations = new ArrayList<>();
        for (String loc : LOCATION_KEYWORDS) {
            if (q.contains(loc)) {
                foundLocations.add(loc);
            }
        }
        
        if (!foundLocations.isEmpty()) {
            return foundLocations.get(0);
        }
        
        return null;
    }

    private String extractArtist(String q) {
        String temp = q;
        
        for (String loc : LOCATION_KEYWORDS) {
            temp = temp.replace(loc, "");
        }
        
        String result = temp;
        
        for (String indicator : ARTIST_INDICATORS) {
            result = result.replace(indicator, "");
        }
        
        for (String stop : STOP_WORDS) {
            result = result.replace(stop, "");
        }
        
        result = result.trim();
        
        return result.isEmpty() ? null : result;
    }
}
