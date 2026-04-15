package com.umesh.unipay_1.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyCardsResponse {

    private boolean success;
    private List<MyCardDto> data;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MyCardDto {
        private Long cardId;
        private String cardUid;

        @JsonProperty("isBlocked")
        private boolean isBlocked;

        @JsonProperty("isActive")
        private boolean isActive;

        private String blockedReason;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime linkedAt;

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime lastUsedAt;
    }
}
