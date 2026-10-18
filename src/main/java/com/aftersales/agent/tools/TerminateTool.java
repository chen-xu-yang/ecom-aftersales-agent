package com.aftersales.agent.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * 终止工具（用于智能体无法继续时主动中止任务）
 */
public class TerminateTool {

    @Tool(description = """
            Abort the task when you cannot proceed further, for example when the request is
            outside the supported scope or the required information is unavailable.
            Do not use this tool to deliver your final answer. Reply to the user directly and
            stop calling tools instead; the task ends automatically.
            """)
    public String doTerminate() {
        return "任务结束";
    }
}
