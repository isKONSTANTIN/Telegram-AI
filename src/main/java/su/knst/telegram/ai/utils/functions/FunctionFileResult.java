package su.knst.telegram.ai.utils.functions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FunctionFileResult extends FunctionResult {
    public final File file;
    public final String filename;

    public FunctionFileResult(File file, String filename) {
        super(true);

        this.file = file;
        this.filename = filename;
    }

    public static FunctionResult fromString(String content, String filename) {
        try {
            Path tmpFile = Files.createTempFile(Path.of("/","tmp","/"), "knst_ai_response_",".txt");
            Files.write(tmpFile, content.getBytes());

            return new FunctionFileResult(tmpFile.toFile(), filename);
        }catch (IOException e) {
            return new FunctionError("Failed to create temporary file");
        }
    }
}
