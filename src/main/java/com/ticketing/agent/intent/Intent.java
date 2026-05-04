package com.ticketing.agent.intent;

public enum Intent {
    SESSION_QUERY("场次查询"),
    ARTIST_QUERY("艺人演唱会查询"),
    TICKET_PURCHASE("购票咨询"),
    TICKET_REFUND("退票咨询"),
    ACCOUNT_ISSUE("账户问题"),
    GENERAL_QUESTION("通用问题"),
    UNKNOWN("未知");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
