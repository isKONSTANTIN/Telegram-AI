package su.knst.telegram.ai.utils.functions;

import java.io.File;

public class FunctionImageResult extends FunctionResult {
    public final String url;
    public final File file;

    public FunctionImageResult(String url) {
        super(url != null && !url.isBlank());

        this.url = url;
        this.file = null;
    }

    public FunctionImageResult(File file) {
        super(file != null && file.exists());

        this.file = file;
        this.url = null;
    }
}
