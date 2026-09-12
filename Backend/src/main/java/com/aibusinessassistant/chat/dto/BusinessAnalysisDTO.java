package com.aibusinessassistant.chat.dto;

import java.util.List;

public record BusinessAnalysisDTO(
        String summary,
        List<String> insights,
        List<String> recommendations
) {}
