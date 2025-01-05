package su.knst.telegram.ai.workers;

import app.finwave.rct.reactive.property.Property;
import app.finwave.rct.reactive.value.Value;
import app.finwave.tat.utils.Pair;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.ChatsDatabase;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.jooq.tables.records.AiContextsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.managers.AiContextManager;
import su.knst.telegram.ai.managers.AiMessagesManager;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.AiPresetsManager;
import su.knst.telegram.ai.utils.ArrayDeserializer;
import su.knst.telegram.ai.utils.ChatMessagesBuilder;
import su.knst.telegram.ai.utils.ContentMeta;
import su.knst.telegram.ai.utils.functions.FunctionError;
import su.knst.telegram.ai.utils.functions.FunctionResult;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static su.knst.telegram.ai.utils.ContentPartParser.*;

@Singleton
public class AiWorker {
    protected static final Logger log = LoggerFactory.getLogger(AiWorker.class);
    protected ExecutorService executor = Executors.newCachedThreadPool();

    protected List<Tool> toolsList;
    protected Map<String, Tool> toolsMap;

    protected AiTools aiTools;

    protected Property<AiConfig> config;
    protected AiContextManager contextManager;
    protected AiMessagesManager messagesManager;
    protected AiPresetsManager presetsManager;
    protected AiModelsManager modelsManager;

    protected ChatsDatabase chatsDatabase;

    protected Value<List<OpenAI>> servers;

    @Inject
    public AiWorker(ConfigWorker configWorker, AiContextManager contextManager, AiMessagesManager messagesManager, AiPresetsManager presetsManager, AiModelsManager modelsManager, DatabaseWorker databaseWorker, AiTools aiTools) {
        this.config = configWorker.ai;

        this.aiTools = aiTools;
        this.toolsList = aiTools.getTools();
        this.toolsMap = aiTools.getToolsMap();

        this.contextManager = contextManager;
        this.messagesManager = messagesManager;
        this.presetsManager = presetsManager;
        this.modelsManager = modelsManager;

        this.chatsDatabase = databaseWorker.get(ChatsDatabase.class);

        this.servers = config.map((c) ->
                Arrays.stream(c.servers).map((s) -> {
                    OpenAI.Builder builder = OpenAI.newBuilder();

                    if (!s.token.isBlank())
                        builder.apiKey(s.token);

                    if (!s.customUrl.isBlank())
                        builder.baseUrl(s.customUrl);

                    if (!s.project.isBlank())
                        builder.project(s.project);

                    if (!s.organization.isBlank())
                        builder.organization(s.organization);

                    return builder.build();
                }).toList()
        );

        aiTools.setWorker(this);
    }

    public AiContextManager getContextManager() {
        return contextManager;
    }

    public AiMessagesManager getMessagesManager() {
        return messagesManager;
    }

    public AiPresetsManager getPresetsManager() {
        return presetsManager;
    }

    public AiModelsManager getModelsManager() {
        return modelsManager;
    }

    public AiTools getAiTools() {
        return aiTools;
    }

    public Optional<AiContextsRecord> initContext(long chatId, long presetId) {
        log.debug("Creating new context for chat #{}", chatId);

        Optional<AiPresetsRecord> preset = presetsManager.getPreset(presetId);

        if (preset.isEmpty())
            return Optional.empty();

        Optional<AiContextsRecord> result = contextManager.newContext(chatId, presetId);

        if (result.isEmpty())
            return Optional.empty();

        String systemMessage = preset.get().getText();

        messagesManager.pushMessage(result.get().getId(), chatId, "system", contentToJson(List.of(
                new ContentPart.TextContentPart(systemMessage)
        )));

        return result;
    }

    public Optional<AiContextsRecord> updateContext(long contextId, long presetId) {
        Optional<AiPresetsRecord> preset = presetsManager.getPreset(presetId);

        if (preset.isEmpty())
            return Optional.empty();

        Optional<AiContextsRecord> result = contextManager.setContextPreset(contextId, presetId);

        if (result.isEmpty())
            return Optional.empty();

        String systemMessage = preset.get().getText();

        messagesManager.pushMessage(result.get().getId(), result.get().getChatId(), "system", contentToJson(List.of(
                new ContentPart.TextContentPart(systemMessage)
        )));

        return result;
    }

    public Optional<AiPresetsRecord> deletePreset(long presetId, long replacePresetId) {
        contextManager.replacePreset(presetId, replacePresetId);

        return presetsManager.deletePreset(presetId);
    }

    protected List<ToolCall> checkUndoneRequests(List<AiMessagesRecord> messages) {
        AiMessagesRecord lastMessage = messages.get(messages.size() - 1);

        if (!lastMessage.getRole().equals("assistant"))
            return List.of();

        ContentMeta meta = jsonToContent(lastMessage.getContent())
                .stream()
                .filter((o) -> o instanceof ContentMeta)
                .map((o) -> (ContentMeta) o)
                .findFirst()
                .orElse(null);

        if (meta == null)
            return List.of();

        return meta.toolCalls()
                .stream()
                .filter((e) -> e.getAsJsonObject().asMap().containsKey("function"))
                .map((e) -> GSON.fromJson(e, ToolCall.FunctionToolCall.class))
                .map((e) -> (ToolCall) e)
                .toList();
    }

    protected FunctionResult runTool(ToolCall.FunctionToolCall functionCall, long contextId, long chatId) {
        Gson g = GSON
                .newBuilder()
                .registerTypeAdapter(new TypeToken<Map<String, String>>(){}.getType(), new ArrayDeserializer())
                .create();

        String name = functionCall.function().name();
        Map<String, String> args;

        try {
            args = g.fromJson(functionCall.function().arguments(), new TypeToken<Map<String, String>>(){}.getType());
        }catch (Exception e) {
            return new FunctionError("Function call failed: invalid arguments json");
        }

        return aiTools.run(name, chatId, contextId, args);
    }

    protected boolean runTools(List<ToolCall> calls, long contextId, long chatId, Consumer<ContextUpdate> updatesConsumer) {
        List<ContextUpdate> updates = calls.stream()
                .filter(c -> c instanceof ToolCall.FunctionToolCall)
                .map((c) -> {
                    FunctionResult result;

                    try {
                        result = runTool((ToolCall.FunctionToolCall) c, contextId, chatId);
                    }catch (Exception e) {
                        result = new FunctionError("Function call failed: unknown error");
                    }

                    return Pair.of(result, c.id());
                })
                .map(r -> new ContextUpdate(contextId, chatId, null, r.first(), r.second()))
                .toList();

        updates.forEach(updatesConsumer);

        return !updates.isEmpty();
    }

    protected void ask(long contextId, long chatId, AiPresetsRecord preset, AiModelsRecord model, Consumer<ContextUpdate> updatesConsumer) {
        List<AiMessagesRecord> messages = messagesManager.getMessages(contextId);
        List<ToolCall> undoneRequests = checkUndoneRequests(messages);

        if (!undoneRequests.isEmpty())
            runTools(undoneRequests, contextId, chatId, updatesConsumer);

        ChatCompletion chatCompletion = servers.get().get(model.getServer())
                .chatClient()
                .createChatCompletion(createChatCompletionRequest(messages, preset, model));

        Usage usage = chatCompletion.usage();
        ChatCompletion.Choice.Message message = chatCompletion.choices().get(0).message();

        modelsManager.addUsage(model.getId(), chatId, usage);

        updatesConsumer.accept(new ContextUpdate(contextId, chatId, message, null, null));

        if (message.toolCalls() != null && runTools(message.toolCalls(), contextId, chatId, updatesConsumer))
            ask(contextId, chatId, preset, model, updatesConsumer);
    }

    public CompletableFuture<Boolean> ask(long contextId, long chatId, Consumer<ContextUpdate> updatesConsumer) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        AiContextsRecord contextRecord = contextManager.getContext(contextId).orElseThrow();
        AiPresetsRecord preset = presetsManager.getPreset(contextRecord.getLastPresetId()).orElseThrow();
        AiModelsRecord modelsRecord = modelsManager.getModel(preset.getModel()).orElseThrow();

        if (!modelsRecord.getEnabled()) {
            result.complete(false);

            return result;
        }

        log.debug("AI asking in #{} context for chat #{}", contextId, chatId);

        executor.execute(() -> {
            try {
                ask(contextId, chatId, preset, modelsRecord, updatesConsumer);
                result.complete(true);
            }catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });

        return result;
    }

    public CompletableFuture<String> generateFilename(String assistantOutput) {
        AiConfig.FilenameGeneration fg = config.get().filenameGeneration;

        if (!fg.useGPT)
            return CompletableFuture.completedFuture(null);

        if (assistantOutput.length() > 64)
            assistantOutput = assistantOutput.substring(0, 61) + "...";

        CreateChatCompletionRequest.Builder builder = CreateChatCompletionRequest.newBuilder();

        builder.tools(Arrays.stream(fg.model.includedTools)
                .map(toolsMap::get)
                .toList());

        builder.messages(List.of(
                        ChatMessage.systemMessage(fg.preset.prompt),
                        ChatMessage.userMessage(assistantOutput)
                ))
                .model(fg.model.model)
                .temperature(fg.preset.temperature)
                .maxTokens(fg.preset.maxTokens)
                .topP(fg.preset.topP)
                .frequencyPenalty(fg.preset.frequencyPenalty)
                .presencePenalty(fg.preset.presencePenalty);

        CreateChatCompletionRequest chatCompletionRequest = builder.build();

        return servers.get().get(fg.serverIndexToUse).chatClient()
                .createChatCompletionAsync(chatCompletionRequest)
                .thenApply((r) -> r.choices().get(0).message().content())
                .thenApply((s) -> {
                    if (!s.contains("."))
                        return s.replaceAll("[^\\p{L}\\p{N}\\s_]", "") + ".txt";

                    return s
                            .substring(0, s.indexOf('.') - 1) // remove all after dot
                            .replaceAll("[^\\p{L}\\p{N}\\s_]", "") // remove special characters
                            + ".txt";
                });
    }

    protected CompletableFuture<Images> createImage(String prompt, String size, String quality) {
        CreateImageRequest.Builder builder = CreateImageRequest.newBuilder()
                .model(config.get().imagineSettings.model)
                .prompt(prompt)
                .size(size)
                .quality(quality);

        return servers.get().get(config.get().imagineSettings.serverIndexToUse)
                .imagesClient()
                .createImageAsync(builder.build());
    }

    protected CreateChatCompletionRequest createChatCompletionRequest(List<AiMessagesRecord> messages, AiPresetsRecord preset, AiModelsRecord modelRecord) {
        CreateChatCompletionRequest.Builder builder = CreateChatCompletionRequest.newBuilder();

        builder.tools(Arrays.stream(modelRecord.getIncludedTools())
                .map(toolsMap::get)
                .toList());

        builder.messages(new ChatMessagesBuilder(messages).build())
                .model(modelRecord.getModel())
                .temperature(preset.getTemperature())
                .maxTokens(preset.getMaxTokens())
                .topP(preset.getTopP())
                .frequencyPenalty(preset.getFrequencyPenalty())
                .presencePenalty(preset.getPresencePenalty());

        return builder.build();
    }
}