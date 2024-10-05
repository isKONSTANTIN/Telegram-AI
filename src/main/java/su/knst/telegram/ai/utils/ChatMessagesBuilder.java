package su.knst.telegram.ai.utils;

import io.github.stefanbratanov.jvm.openai.ChatMessage;
import io.github.stefanbratanov.jvm.openai.ContentPart;
import io.github.stefanbratanov.jvm.openai.ToolCall;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static su.knst.telegram.ai.utils.ContentPartParser.GSON;
import static su.knst.telegram.ai.utils.ContentPartParser.jsonToContent;

public class ChatMessagesBuilder {
    protected ArrayList<AiMessagesRecord> messagesRecords = new ArrayList<>();

    public ChatMessagesBuilder(List<AiMessagesRecord> messages) {
        this.messagesRecords.addAll(messages);
    }

    public ChatMessagesBuilder append(AiMessagesRecord record) {
        messagesRecords.add(record);

        return this;
    }

    protected ChatMessage buildMessage(AiMessagesRecord r) {
        switch (r.getRole()) {
            case "system" -> {
                ContentPart.TextContentPart textContentPart = jsonToContent(r.getContent())
                        .stream()
                        .filter((o) -> o instanceof ContentPart.TextContentPart)
                        .map((o) -> (ContentPart.TextContentPart) o)
                        .findFirst()
                        .orElseThrow();

                return ChatMessage.systemMessage(textContentPart.text());
            }
            case "user" -> {
                List<ContentPart> textContentPart = jsonToContent(r.getContent())
                        .stream()
                        .filter((o) -> o instanceof ContentPart)
                        .map((o) -> (ContentPart) o)
                        .toList();

                return new ChatMessage.UserMessage.UserMessageWithContentParts(textContentPart, Optional.empty());
            }
            case "assistant" -> {
                List<Object> content = jsonToContent(r.getContent());

                String text = content
                        .stream()
                        .filter((o) -> o instanceof ContentPart.TextContentPart)
                        .map((o) -> (ContentPart.TextContentPart) o)
                        .findFirst()
                        .map(ContentPart.TextContentPart::text)
                        .orElse(null);

                ContentMeta meta = content
                        .stream()
                        .filter((o) -> o instanceof ContentMeta)
                        .map((o) -> (ContentMeta) o)
                        .findFirst()
                        .orElse(null);

                List<ToolCall> functionToolCalls = null;

                if (meta != null) {
                    functionToolCalls = meta.toolCalls()
                            .stream()
                            .filter((e) -> e.getAsJsonObject().asMap().containsKey("function"))
                            .map((e) -> GSON.fromJson(e, ToolCall.FunctionToolCall.class))
                            .map((e) -> (ToolCall) e)
                            .toList();
                }

                return meta != null && !functionToolCalls.isEmpty() ?
                        ChatMessage.assistantMessage(text, functionToolCalls) :
                        ChatMessage.assistantMessage(text);
            }
            case "tool" -> {
                List<Object> content = jsonToContent(r.getContent());

                ContentPart.TextContentPart textContentPart = content
                        .stream()
                        .filter((o) -> o instanceof ContentPart.TextContentPart)
                        .map((o) -> (ContentPart.TextContentPart) o)
                        .findFirst()
                        .orElseThrow();

                ContentMeta meta = content
                        .stream()
                        .filter((o) -> o instanceof ContentMeta)
                        .map((o) -> (ContentMeta) o)
                        .findFirst()
                        .orElseThrow();

                return ChatMessage.toolMessage(textContentPart.text(), meta.toolCallId());
            }
        }

        return null;
    }

    public List<ChatMessage> build() {
        return messagesRecords.stream()
                .map(this::buildMessage)
                .toList();
    }
}
