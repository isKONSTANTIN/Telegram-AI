package su.knst.telegram.ai.utils.functions;

import com.google.gson.JsonElement;

import java.security.InvalidParameterException;
import java.util.Map;

public interface FunctionExecutor {
    FunctionResult run(long chatId, long contextId, Map<String, JsonElement> args) throws InvalidParameterException;
}
