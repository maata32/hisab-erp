package com.minierp.shared.util;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public <R> PageResponse<R> map(java.util.function.Function<T, R> fn) {
        return new PageResponse<>(
                content.stream().map(fn).toList(),
                page, size, totalElements, totalPages, first, last);
    }
}
