package su.knst.telegram.ai.utils.functions;

public class FunctionReactionResult extends FunctionResult {
    public final String emoji;

    public FunctionReactionResult(String emoji) {
        super(true);

        this.emoji = emoji;
    }
}
