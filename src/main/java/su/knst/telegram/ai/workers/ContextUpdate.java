package su.knst.telegram.ai.workers;

import io.github.stefanbratanov.jvm.openai.ChatCompletion;
import su.knst.telegram.ai.utils.functions.FunctionResult;

import java.util.List;

public record ContextUpdate(long contextId, long chatId, ChatCompletion.Choice.Message newMessage, FunctionResult functionResult, String callId) {
}
