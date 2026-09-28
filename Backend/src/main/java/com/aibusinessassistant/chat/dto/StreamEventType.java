package com.aibusinessassistant.chat.dto;

public enum StreamEventType {
    CHUNK,
    TOOL_STARTED,
    TOOL_COMPLETED,
    DONE,
    ERROR
}
