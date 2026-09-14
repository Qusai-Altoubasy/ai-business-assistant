package com.aibusinessassistant.common.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;

@ApplicationScoped
@Slf4j
public class CommonTools {

    @Tool("""
        Returns the current application date.
        Use this tool when the user refers to relative dates such as
        today, yesterday, this month, last month, or this year.
        """)
    public LocalDate getCurrentDate() {
        LocalDate currentDate = LocalDate.now();

        log.info("Tool called: getCurrentDate (currentDate={})", currentDate);

        return currentDate;
    }
}