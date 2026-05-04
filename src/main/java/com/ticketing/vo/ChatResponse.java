package com.ticketing.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String answer;
    private String intent;
    private Boolean needTransfer;
    private Long faqId;
    private Boolean aiGenerated;
    private Boolean isRichText;
}
