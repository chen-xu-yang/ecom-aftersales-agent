package com.aftersales.agent.chatmemory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileBasedChatMemory 测试：追加、读回、持久化与清理。
 * <p>
 * 该组件默认未启用（AftersalesApp 中使用内存记忆），但作为可选能力仍需保证可用。
 */
@DisplayName("基于文件的对话记忆")
class FileBasedChatMemoryTest {

    @Test
    @DisplayName("追加消息后可按会话读回")
    void addAndGetMessages(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());

        memory.add("chat-1", List.of(new UserMessage("你好"), new AssistantMessage("您好，请描述售后问题")));

        List<Message> messages = memory.get("chat-1");
        assertEquals(2, messages.size());
        assertEquals("你好", messages.get(0).getText());
        assertEquals("您好，请描述售后问题", messages.get(1).getText());
    }

    @Test
    @DisplayName("多次追加按顺序累积")
    void appendsInOrder(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());

        memory.add("chat-1", List.of(new UserMessage("第一问")));
        memory.add("chat-1", List.of(new AssistantMessage("第一答")));

        List<Message> messages = memory.get("chat-1");
        assertEquals(2, messages.size());
        assertEquals("第一问", messages.get(0).getText());
        assertEquals("第一答", messages.get(1).getText());
    }

    @Test
    @DisplayName("写入磁盘后，新实例可以读回（持久化）")
    void persistsAcrossInstances(@TempDir Path tempDir) {
        FileBasedChatMemory first = new FileBasedChatMemory(tempDir.toString());
        first.add("chat-persist", List.of(new UserMessage("持久化问题")));

        FileBasedChatMemory second = new FileBasedChatMemory(tempDir.toString());
        List<Message> messages = second.get("chat-persist");

        assertEquals(1, messages.size());
        assertEquals("持久化问题", messages.get(0).getText());
    }

    @Test
    @DisplayName("不同会话互不干扰")
    void isolatesConversations(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());

        memory.add("chat-a", List.of(new UserMessage("A 的问题")));
        memory.add("chat-b", List.of(new UserMessage("B 的问题")));

        assertEquals("A 的问题", memory.get("chat-a").get(0).getText());
        assertEquals("B 的问题", memory.get("chat-b").get(0).getText());
    }

    @Test
    @DisplayName("clear 删除会话文件，之后读到空列表")
    void clearRemovesConversation(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        memory.add("chat-clear", List.of(new UserMessage("待清理")));

        memory.clear("chat-clear");

        assertTrue(memory.get("chat-clear").isEmpty());
        assertEquals(0, tempDir.toFile().listFiles((dir, name) -> name.startsWith("chat-clear")).length);
    }

    @Test
    @DisplayName("未写入过的会话返回空列表")
    void unknownConversationReturnsEmpty(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        assertTrue(memory.get("never-used").isEmpty());
    }

    @Test
    @DisplayName("会话 ID 允许中文，安全校验不应误伤正常 chatId")
    void allowsChineseConversationId(@TempDir Path tempDir) {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());

        memory.add("售后会话-1", List.of(new UserMessage("你好")));

        assertEquals(1, memory.get("售后会话-1").size());
    }

    @Test
    @DisplayName("会话 ID 不应允许路径穿越（chatId 来自请求参数）")
    void rejectsConversationIdTraversal(@TempDir Path tempDir) throws Exception {
        FileBasedChatMemory memory = new FileBasedChatMemory(tempDir.toString());
        File escaped = tempDir.getParent().resolve("escaped-memory.kryo").toFile();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> memory.add("../escaped-memory", List.of(new UserMessage("越界"))),
                    "越界会话 ID 应被拒绝");
            assertTrue(!escaped.exists(), "不应在记忆目录之外写入文件：" + escaped.getAbsolutePath());
        } finally {
            java.nio.file.Files.deleteIfExists(escaped.toPath());
        }
    }
}
