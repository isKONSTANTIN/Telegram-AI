package su.knst.telegram.ai.utils.functions;

public class FunctionTextResult extends FunctionResult {
    public final String result;

    public FunctionTextResult(String result) {
        super(true);

        this.result = result;
    }
}
