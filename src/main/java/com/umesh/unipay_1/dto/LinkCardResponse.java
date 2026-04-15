package com.umesh.unipay_1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkCardResponse {

    private boolean success;
    private String message;
    private CardData data;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CardData {
        private Long cardId;
        private String cardUid;
        private String linkedTo;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime linkedAt;
    }
}
