package su.knst.telegram.ai.utils.functions;

import java.security.InvalidParameterException;
import java.util.Map;

public interface FunctionExecutor {
    FunctionResult run(long chatId, long contextId, Map<String, String> args) throws InvalidParameterException;
}
