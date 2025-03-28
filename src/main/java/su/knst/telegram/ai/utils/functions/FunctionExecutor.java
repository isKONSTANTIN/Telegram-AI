package su.knst.telegram.ai.utils.functions;

import com.google.gson.JsonElement;
import su.knst.telegram.ai.handlers.ChatHandler;

import java.security.InvalidParameterException;
import java.util.Map;

public interface FunctionExecutor {
    FunctionResult run(ChatHandler chatHandler, long contextId, Map<String, JsonElement> args) throws InvalidParameterException;
}
