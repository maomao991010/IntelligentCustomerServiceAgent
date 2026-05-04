package com.ticketing.agent.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticketing.agent.ai.ArtistAliasResolver;
import com.ticketing.agent.tool.QueryParser.QueryResult;
import com.ticketing.dao.SessionDao;
import com.ticketing.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SessionQueryTool {

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private QueryParser queryParser;

    @Autowired(required = false)
    private ArtistAliasResolver artistAliasResolver;

    public List<Session> searchSessions(String question) {
        log.info("Session查询工具: 原始问题={}", question);

        QueryResult queryResult = queryParser.parse(question);
        log.info("解析结果: type={}, artist={}, location={}", 
                 queryResult.getType(), queryResult.getArtist(), queryResult.getLocation());

        String searchArtist = queryResult.getArtist();
        String searchLocation = queryResult.getLocation();
        String searchKeyword = queryResult.getGeneralKeyword();

        if (queryResult.getType() == QueryParser.QueryType.ARTIST_AND_LOCATION || 
            queryResult.getType() == QueryParser.QueryType.ARTIST) {
            if (artistAliasResolver != null && searchArtist != null) {
                String resolvedName = artistAliasResolver.resolveArtistName(question, searchArtist);
                if (resolvedName != null && !resolvedName.equals(searchArtist)) {
                    log.info("艺人别名解析成功: {} -> {}", searchArtist, resolvedName);
                    searchArtist = resolvedName;
                }
            }
        }

        log.info("最终搜索 - artist={}, location={}, keyword={}", searchArtist, searchLocation, searchKeyword);

        try {
            Page<Session> page = new Page<>(1, 5);
            IPage<Session> result;
            
            if (queryResult.getType() == QueryParser.QueryType.ARTIST_AND_LOCATION) {
                result = sessionDao.selectSessionPageWithArtistAndLocation(page, searchArtist, searchLocation);
            } else if (queryResult.getType() == QueryParser.QueryType.ARTIST) {
                result = sessionDao.selectSessionPageWithArtistAndLocation(page, searchArtist, null);
            } else if (queryResult.getType() == QueryParser.QueryType.LOCATION) {
                result = sessionDao.selectSessionPageWithArtistAndLocation(page, null, searchLocation);
            } else {
                result = sessionDao.selectSessionPageWithSearch(page, searchKeyword);
            }
            
            List<Session> sessions = result.getRecords();
            log.info("查询到 {} 条相关场次记录", sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("查询场次失败", e);
            return new ArrayList<>();
        }
    }

    public String formatSessions(List<Session> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "没有找到相关的演唱会信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("为您找到以下演唱会信息：\n\n");

        int index = 1;
        for (Session session : sessions) {
            sb.append(index).append(". ").append(session.getActivityName()).append("\n");
            if (session.getArtist() != null && !session.getArtist().isEmpty()) {
                sb.append("   艺人：").append(session.getArtist()).append("\n");
            }
            if (session.getDate() != null) {
                sb.append("   日期：").append(session.getDate());
                if (session.getTime() != null) {
                    sb.append(" ").append(session.getTime());
                }
                sb.append("\n");
            }
            if (session.getVenue() != null) {
                sb.append("   地点：").append(session.getVenue()).append("\n");
            }
            if (session.getMinPrice() != null && session.getMaxPrice() != null) {
                sb.append("   票价：¥").append(session.getMinPrice()).append(" - ¥").append(session.getMaxPrice()).append("\n");
            }
            if (session.getRemainingSeats() != null) {
                sb.append("   剩余座位：").append(session.getRemainingSeats()).append("张\n");
            }
            sb.append("\n");
            index++;
        }

        return sb.toString();
    }
}
