package su.knst.telegram.ai.utils.functions;

public class FunctionError extends FunctionResult {
    public final String error;

    public FunctionError(String error) {
        super(false);

        this.error = error;
    }
}
