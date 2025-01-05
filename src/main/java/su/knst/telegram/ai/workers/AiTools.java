package su.knst.telegram.ai.workers;

import app.finwave.tat.BotCore;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.parser.ParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.Function;
import io.github.stefanbratanov.jvm.openai.Images;
import io.github.stefanbratanov.jvm.openai.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.knst.telegram.ai.managers.AiContextManager;
import su.knst.telegram.ai.managers.AiMessagesManager;
import su.knst.telegram.ai.utils.functions.*;
import su.knst.telegram.ai.utils.parsers.Markdown2DocxConverter;
import su.knst.telegram.ai.utils.parsers.TextConverters;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
public class AiTools {
    protected static final Logger log = LoggerFactory.getLogger(AiTools.class);

    protected List<Tool> tools = new ArrayList<>();
    protected HashMap<String, FunctionExecutor> functionExecutors = new HashMap<>();
    protected HashMap<String, Tool> toolsMap = new HashMap<>();

    public static final FunctionResult NOT_FOUND = new FunctionError("Function not found");
    public static final FunctionResult FAILED = new FunctionError("Function run failed");

    protected AiContextManager contextManager;
    protected AiMessagesManager messagesManager;

    protected AiWorker worker;

    @Inject
    public AiTools(AiContextManager contextManager, AiMessagesManager messagesManager) {
        this.contextManager = contextManager;
        this.messagesManager = messagesManager;

        buildTools();
    }

    public void setWorker(AiWorker worker) {
        this.worker = worker;
    }

    public FunctionResult run(String name, long chatId, long contextId, Map<String, String> args) {
        FunctionExecutor executor = functionExecutors.get(name);

        if (executor == null)
            return NOT_FOUND;

        FunctionResult result;

        try {
            log.debug("AI run function: {} for chat #{}", name, chatId);

            result = executor.run(chatId, contextId, args);
        }catch (Exception e) {
            e.printStackTrace();

            result = FAILED;
        }

        return result;
    }

    public List<Tool> getTools() {
        return Collections.unmodifiableList(tools);
    }

    public Map<String, Tool> getToolsMap() {
        return Collections.unmodifiableMap(toolsMap);
    }

    protected void buildTools() {
        function("calculate", "Use this function to calculate math expressions",
                (chatId, contextId, args) -> {
                    try {
                        return new FunctionTextResult(new Expression(args.get("expression"))
                                .evaluate()
                                .getNumberValue()
                                .toString()
                        );
                    } catch (EvaluationException | ParseException e) {
                        return new FunctionError("Invalid expression");
                    }
                },
                Parameter.of("expression", "string", "Expression to calculate", true)
        );

        function("react", "Use this function to react to the user's last message",
                (chatId, contextId, args) -> new FunctionReactionResult(args.get("emoji")),
                Parameter.of("emoji", "string", "Emoji to send", true)
        );

        function("read_website", "Use this function to see website text content",
                (chatId, contextId, args) -> {
                    String url = args.get("url");

                    try {
                        return new FunctionTextResult(TextConverters.parseHtml(FileDownloader.downloadFile(url).get()).orElseThrow());
                    } catch (Exception e) {
                        return new FunctionError("Fail to read");
                    }
                },
                Parameter.of("url", "string", "Url to read", true)
        );

        function("imagine", "Use this function to imagine and send photo to user using DALL·E 3. Do NOT share result link",
                (chatId, contextId, args) -> {
                    String prompt = args.get("prompt");
                    String size = args.get("size");
                    boolean hd = Optional.ofNullable(args.get("hd")).map(Boolean::parseBoolean).orElse(false);

                    CompletableFuture<Images> futureImage = worker.createImage(prompt, size, hd ? "hd" : "standard");

                    try {
                        return new FunctionImageResult(futureImage.get().data().get(0).url());
                    }catch (Exception e) {
                        e.printStackTrace();
                        return new FunctionError("Fail to imagine");
                    }
                },
                Parameter.of("prompt", "string", "Provide an effective prompt for DALL-E 3 to generate beautiful and high-quality images. Always in English, using descriptive words separated by spaces, without quotes or punctuation.", true),
                Parameter.of("size", List.of("1024x1024", "1024x1792", "1792x1024"), "Size of new image", true),
                Parameter.of("hd", "boolean", "Specify true if you need to generate an image at the maximum resolution", false)
        );

        function("send_file", "Use this function to send file to user. Do NOT share result link",
                (chatId, contextId, args) -> FunctionFileResult.fromString(args.get("content"), args.get("filename")),
                Parameter.of("content", "string", "File's content", true),
                Parameter.of("filename", "string", "Filename with extension", true)
        );

        function("send_docx_file", "Use this function to send docx file to user. Do NOT share result link for downloading",
                (chatId, contextId, args) -> {
                    String markdown = args.get("markdown");
                    String filename = args.get("filename");

                    Optional<File> result = Markdown2DocxConverter.convert(markdown);

                    if (result.isEmpty())
                        return new FunctionError("Failed to convert markdown");

                    return new FunctionFileResult(result.get(), filename);
                },
                Parameter.of("markdown", "string", "File's text content in markdown", true),
                Parameter.of("filename", "string", "Filename with extension", true)
                );
    }

    protected void function(String name, String description, FunctionExecutor executor, Parameter... parameters) {
        Tool tool = Tool.functionTool(Function.newBuilder()
                .name(name)
                .description(description)
                .parameters(wrapParameters(parameters))
                .build()
        );

        functionExecutors.put(name, executor);
        tools.add(tool);
        toolsMap.put(name, tool);
    }

    protected static Map<String, Object> wrapParameters(Parameter... parameters) {
        HashMap<String, Object> properties = new HashMap<>();
        ArrayList<String> required = new ArrayList<>();

        for (Parameter parameter : parameters) {
            properties.put(parameter.name, parameter.toSchema());

            if (parameter.required)
                required.add(parameter.name);
        }

        HashMap<String, Object> result = new HashMap<>(Map.of(
                "type", "object", // always accepting object as arguments for function
                "properties", properties
        ));

        if (!required.isEmpty())
            result.put("required", required);

        return result;
    }

    public static class Parameter {
        public String name;
        public String type;
        public String description;
        public List<String> enumVariants;
        public boolean required;

        public Parameter(String name, String type, String description, List<String> enumVariants, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.enumVariants = enumVariants;
            this.required = required;
        }

        public Object toSchema() {
            HashMap<String, Object> schema = new HashMap<>();

            if (type.startsWith("array>")) {
                String[] types = type.split(">");

                schema.put("type", types[0]);
                schema.put("items", Map.of("type", types[1]));
            }else
                schema.put("type", type);

            if (description != null)
                schema.put("description", description);

            if (enumVariants != null)
                schema.put("enum", enumVariants);

            return schema;
        }

        public static Parameter of(String name, String type, String description, List<String> enumVariants, boolean required) {
            return new Parameter(name, type, description, enumVariants, required);
        }

        public static Parameter of(String name, String type, String description, boolean required) {
            return of(name, type, description, null, required);
        }

        public static Parameter of(String name, String type, boolean required) {
            return of(name, type, null, null, required);
        }

        public static Parameter of(String name, List<String> enumVariants, String description, boolean required) {
            return of(name, "string", description, enumVariants, required);
        }

        public static Parameter of(String name, List<String> enumVariants, boolean required) {
            return of(name, "string", null, enumVariants, required);
        }
    }
}
