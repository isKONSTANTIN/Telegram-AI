package su.knst.telegram.ai.logging;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class SystemStreamsHandler {
    protected static PrintStream defaultOutStream;
    protected static PrintStream defaultErrStream;
    protected FileOutputStream fileStream;

    static {
        defaultOutStream = System.out;
        defaultErrStream = System.err;
    }

    public SystemStreamsHandler(FileOutputStream fileStream) {
        this.fileStream = fileStream;
    }

    public void replaceStreams() {
        System.setOut(new PrintStream(new StreamDuplicator(defaultOutStream, fileStream)));
        System.setErr(new PrintStream(new StreamDuplicator(defaultErrStream, fileStream)));
    }

    public void defaultStreams() {
        System.setOut(defaultOutStream);
        System.setErr(defaultErrStream);
    }
}
