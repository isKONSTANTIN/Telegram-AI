package su.knst.telegram.ai.utils.functions;

public class FunctionImageResult extends FunctionResult {
    public final String url;

    public FunctionImageResult(String url) {
        super(url != null && !url.isBlank());

        this.url = url;
    }
}
