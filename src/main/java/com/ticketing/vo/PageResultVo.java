package com.ticketing.vo;

import lombok.Data;
import java.util.List;

@Data
public class PageResultVo<T> {
    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;
}
