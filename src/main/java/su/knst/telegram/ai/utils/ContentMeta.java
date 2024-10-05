package su.knst.telegram.ai.utils;

import com.google.gson.JsonElement;

import java.util.List;

public record ContentMeta(List<JsonElement> toolCalls, String toolCallId) {
}
