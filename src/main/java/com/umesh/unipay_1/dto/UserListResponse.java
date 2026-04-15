package com.umesh.unipay_1.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {
    private boolean success;
    private UserPageData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserPageData {
        private List<UserResponse> users;
        private int totalPages;
        private long totalElements;
        private int currentPage;
    }
}
