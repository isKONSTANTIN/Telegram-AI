package su.knst.telegram.ai.utils.functions;

public class FunctionFileResult extends FunctionResult {
    public final String content;
    public final String filename;

    public FunctionFileResult(String content, String filename) {
        super(true);

        this.content = content;
        this.filename = filename;
    }
}
