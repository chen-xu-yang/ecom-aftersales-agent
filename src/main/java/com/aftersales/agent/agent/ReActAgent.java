package com.aftersales.agent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aftersales.agent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考和行动
     * <p>
     * 模型没有发起工具调用时，视为已经给出最终答复：标记 FINISHED 并返回该答复，
     * 避免循环空转直到步数耗尽。
     *
     * @return 步骤执行结果
     */
    @Override
    public String step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 思考阶段异常时保留 ERROR 状态，由调用方按错误处理
                if (getState() != AgentState.ERROR) {
                    setState(AgentState.FINISHED);
                }
                return getLastAssistantText();
            }
            // 再行动
            return act();
        } catch (Exception e) {
            setState(AgentState.ERROR);
            log.error("步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 读取消息列表末尾的助手消息文本，作为最终答复
     *
     * @return 最终答复文本
     */
    private String getLastAssistantText() {
        Message last = CollUtil.getLast(getMessageList());
        if (last instanceof AssistantMessage assistantMessage
                && StrUtil.isNotBlank(assistantMessage.getText())) {
            return assistantMessage.getText();
        }
        return "任务完成";
    }
}
