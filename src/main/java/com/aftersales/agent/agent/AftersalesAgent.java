package com.aftersales.agent.agent;

import com.aftersales.agent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 售后处理智能体（基于 ReAct 模式，可自主调用工具完成售后任务）
 */
@Component
public class AftersalesAgent extends ToolCallAgent {

    public AftersalesAgent(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("aftersalesAgent");
        String SYSTEM_PROMPT = """
                你是电商平台的售后处理智能体，职责范围是售后场景：退换货、物流异常、商品质量、价保与发票。
                可用工具与使用规则：
                1. searchPolicy：检索平台售后规则。涉及平台自身的退换货条件、时效、运费承担、赔付标准、价保、发票等政策细节时，
                   必须先调用 searchPolicy 并依据规则原文回答；检索不到时提示用户转人工客服，不得凭记忆编造平台政策。
                2. searchWeb：联网搜索平台外公开信息。涉及快递公司、品牌方等第三方主体的保价、理赔、时效或服务条款时，
                   必须先调用 searchWeb 获取公开信息，再结合平台规则回答。你具备联网搜索工具，不要声称自己没有查询外部信息的权限或工具。
                3. readFile / writeFile / generatePDF：需要保存处理记录或生成处理单时使用。
                4. terminate：仅在确实无法继续时中止任务，不要用它来交付最终答复。
                处理问题时遵守平台公示的售后政策，不承诺超出售后政策的赔付；与售后无关的问题礼貌拒绝。
                始终使用用户所使用的语言回复，默认使用中文。
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                根据用户诉求选择最合适的工具：平台政策优先使用 searchPolicy；
                快递公司、品牌方等第三方主体的公开规则使用 searchWeb；需要留存记录时使用文件或 PDF 工具。
                如果不需要工具就能回答，直接回答即可。
                复杂请求可以拆解，按步骤逐个调用工具。
                每次调用工具后，说明执行结果并给出下一步。
                最终答复直接写在回复里：一旦不再调用工具，任务即结束，你的回复会返回给用户。
                只有在无法继续时才调用 terminate 工具中止任务。
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(8);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
