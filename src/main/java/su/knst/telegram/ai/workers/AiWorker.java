package su.knst.telegram.ai.workers;

import app.finwave.rct.reactive.property.Property;
import app.finwave.rct.reactive.value.Value;
import app.finwave.tat.utils.Pair;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.*;
import su.knst.telegram.ai.managers.*;
import su.knst.telegram.ai.utils.ArrayDeserializer;
import su.knst.telegram.ai.utils.ChatMessagesBuilder;
import su.knst.telegram.ai.utils.ContentMeta;
import su.knst.telegram.ai.utils.functions.FunctionError;
import su.knst.telegram.ai.utils.functions.FunctionResult;
import su.knst.telegram.ai.utils.usage.GeneralUsageType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    protected UsageManager usageManager;

    protected AiMemoryManager memoryManager;

    protected Value<List<OpenAI>> servers;

    @Inject
    public AiWorker(ConfigWorker configWorker,
                    AiContextManager contextManager,
                    AiMemoryManager memoryManager,
                    AiMessagesManager messagesManager,
                    AiPresetsManager presetsManager,
                    AiModelsManager modelsManager,
                    AiTools aiTools,
                    UsageManager usageManager) {
        this.config = configWorker.ai;

        this.aiTools = aiTools;
        this.toolsList = aiTools.getTools();
        this.toolsMap = aiTools.getToolsMap();

        this.contextManager = contextManager;
        this.messagesManager = messagesManager;
        this.presetsManager = presetsManager;
        this.modelsManager = modelsManager;
        this.memoryManager = memoryManager;
        this.usageManager = usageManager;

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

    public UsageManager getUsageManager() {
        return usageManager;
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

    protected FunctionResult runTool(ToolCall.FunctionToolCall functionCall, long contextId, ChatHandler source) {
        Gson g = GSON
                .newBuilder()
                .registerTypeAdapter(new TypeToken<Map<String, String>>(){}.getType(), new ArrayDeserializer())
                .create();

        String name = functionCall.function().name();
        Map<String, JsonElement> args;

        try {
            args = g.fromJson(functionCall.function().arguments(), new TypeToken<Map<String, JsonElement>>(){}.getType());
        }catch (Exception e) {
            return new FunctionError("Function call failed: invalid arguments json");
        }

        return aiTools.run(name, source, contextId, args);
    }

    protected boolean runTools(List<ToolCall> calls, long contextId, ChatHandler source, Consumer<ContextUpdate> updatesConsumer) {
        List<ContextUpdate> updates = calls.stream()
                .filter(c -> c instanceof ToolCall.FunctionToolCall)
                .map((c) -> {
                    FunctionResult result;

                    try {
                        result = runTool((ToolCall.FunctionToolCall) c, contextId, source);
                    }catch (Exception e) {
                        result = new FunctionError("Function call failed: unknown error");
                    }

                    return Pair.of(result, c.id());
                })
                .map(r -> new ContextUpdate(contextId, source.getChatId(), null, r.first(), r.second()))
                .toList();

        updates.forEach(updatesConsumer);

        return !updates.isEmpty();
    }

    protected void ask(long contextId, ChatHandler source, boolean useMemories, AiPresetsRecord preset, AiModelsRecord model, Consumer<ContextUpdate> updatesConsumer) {
        long chatId = source.getChatId();

        List<AiMessagesRecord> messages = messagesManager.getMessages(contextId);
        String includeMemory = null;

        if (useMemories) {
            Optional<AiContextMemoriesRecord> lastMemory = memoryManager.getLastMemory(contextId);
            includeMemory = lastMemory.map(AiContextMemoriesRecord::getMemory).orElse(null);
            long lastSavedMessage = lastMemory.map(AiContextMemoriesRecord::getLastMessage).orElse(0L);

            messages = messages.stream().filter(m -> m.getId() > lastSavedMessage).toList();
        }

        List<ToolCall> undoneRequests = checkUndoneRequests(messages);

        if (!undoneRequests.isEmpty())
            runTools(undoneRequests, contextId, source, updatesConsumer);

        ChatCompletion chatCompletion = servers.get().get(model.getServer())
                .chatClient()
                .createChatCompletion(createChatCompletionRequest(messages, preset, model, includeMemory));

        Usage usage = chatCompletion.usage();
        ChatCompletion.Choice.Message message = chatCompletion.choices().get(0).message();

        usageManager.addUsage(model.getId(), chatId, usage);

        updatesConsumer.accept(new ContextUpdate(contextId, chatId, message, null, null));

        if (message.toolCalls() != null && runTools(message.toolCalls(), contextId, source, updatesConsumer)) {
            ask(contextId, source, true, preset, model, updatesConsumer);
            return;
        }

        if (!useMemories)
            return;

        AiConfig.MemorizingSettings settings = config.get().memorizingSettings;
        long commonSenseMessagesCount = messages.stream().filter((m -> m.getRole().equals("user") || m.getRole().equals("assistant"))).count();

        if (commonSenseMessagesCount - settings.messagesPerMemorizing < settings.keepMessagesInContext)
            return;

        List<AiMessagesRecord> toMemorize = messages.subList(0, settings.messagesPerMemorizing);

        if (messages.get(settings.messagesPerMemorizing).getRole().equals("tool"))
            toMemorize = toMemorize.subList(0, toMemorize.size() - 1);

        var memory = createMemory(toMemorize, includeMemory, chatId);

        List<AiMessagesRecord> finalToMemorize = toMemorize;
        try {
            memory.thenAccept((m) -> memoryManager.addMemory(contextId, finalToMemorize.get(finalToMemorize.size() - 1).getId(), m)).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Boolean> ask(long contextId, ChatHandler source, boolean useMemories, Consumer<ContextUpdate> updatesConsumer) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        AiContextsRecord contextRecord = contextManager.getContext(contextId).orElseThrow();
        AiPresetsRecord preset = presetsManager.getPreset(contextRecord.getLastPresetId()).orElseThrow();
        AiModelsRecord modelsRecord = modelsManager.getModel(preset.getModel()).orElseThrow();

        if (!modelsRecord.getEnabled()) {
            result.complete(false);

            return result;
        }

        log.debug("AI asking in #{} context for chat #{}", contextId, source.getChatId());

        executor.execute(() -> {
            try {
                ask(contextId, source, useMemories, preset, modelsRecord, updatesConsumer);
                result.complete(true);
            }catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });

        return result;
    }

    public CompletableFuture<String> generateFilename(String assistantOutput, long chatId) {
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
                .thenApply((r) -> {
                    usageManager.addUsage(chatId, GeneralUsageType.FILE_NAMING, r.usage(), fg.model.inputPricePer1M, fg.model.outputPricePer1M, LocalDate.now());

                    return r;
                })
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

    protected CompletableFuture<Images> createImage(String prompt, String size, boolean isHd, long chatId) {
        AiConfig.ImagineSettings settings = config.get().imagineSettings;

        CreateImageRequest.Builder builder = CreateImageRequest.newBuilder()
                .model(settings.model)
                .prompt(prompt)
                .size(size)
                .quality(isHd ? "hd" : "standard");

        boolean is1024 = size.equals("1024x1024");

        return servers.get().get(settings.serverIndexToUse)
                .imagesClient()
                .createImageAsync(builder.build()).thenApply((r) -> {
                    double cost;

                    if (isHd) {
                        cost = is1024 ? settings.hd1024QualityPrice : settings.hd1792QualityPrice;
                    }else {
                        cost = is1024 ? settings.standard1024QualityPrice : settings.standard1792QualityPrice;
                    }

                    usageManager.addUsage(chatId, GeneralUsageType.IMAGINING, BigDecimal.valueOf(cost), LocalDate.now());

                    return r;
                });
    }

    protected CompletableFuture<String> createMemory(List<AiMessagesRecord> messages, String lastMemory, long chatId) {
        StringBuilder messagesBuilder = new StringBuilder();

        if (lastMemory != null && !lastMemory.isBlank())
            messagesBuilder.append("Previous memory: \"").append(lastMemory).append("\"\n\n");

        messages.forEach((m) -> {
            messagesBuilder.append(m.getRole()).append(": \"");
            List<Object> content = jsonToContent(m.getContent());

            switch (m.getRole()) {
                case "system" -> {
                    messagesBuilder.append(content
                            .stream()
                            .filter((o) -> o instanceof ContentPart.TextContentPart)
                            .map((o) -> ((ContentPart.TextContentPart) o).text())
                            .findFirst()
                            .orElseThrow()
                    ).append("\"\n\n");
                }
                case "user" -> {
                    Optional<String> text = content
                            .stream()
                            .filter((o) -> o instanceof ContentPart.TextContentPart)
                            .map((o) -> ((ContentPart.TextContentPart) o).text())
                            .findFirst();

                    text.ifPresent(string -> messagesBuilder.append(string).append("\n"));

                    List<String> images = jsonToContent(m.getContent())
                            .stream()
                            .filter((o) -> o instanceof ContentPart.ImageUrlContentPart)
                            .map((o) -> ((ContentPart.ImageUrlContentPart) o).imageUrl().url())
                            .toList();

                    for (String image : images) {
                        messagesBuilder.append("<added image with url: ").append(image).append(">\n");
                    }

                    messagesBuilder.append("\"\n");
                }
                case "assistant" -> {
                    Optional<String> text = content
                            .stream()
                            .filter((o) -> o instanceof ContentPart.TextContentPart)
                            .map((o) -> (ContentPart.TextContentPart) o)
                            .findFirst()
                            .map(ContentPart.TextContentPart::text);

                    Optional<ContentMeta> meta = content
                            .stream()
                            .filter((o) -> o instanceof ContentMeta)
                            .map((o) -> (ContentMeta) o)
                            .findFirst();

                    List<ToolCall.FunctionToolCall> functionToolCalls = null;

                    if (meta.isPresent()) {
                        functionToolCalls = meta.get().toolCalls()
                                .stream()
                                .filter((e) -> e.getAsJsonObject().asMap().containsKey("function"))
                                .map((e) -> GSON.fromJson(e, ToolCall.FunctionToolCall.class))
                                .toList();
                    }


                    text.ifPresent(string -> messagesBuilder.append(string).append("\n"));

                    if (functionToolCalls != null && !functionToolCalls.isEmpty()) {
                        for (ToolCall.FunctionToolCall functionToolCall : functionToolCalls) {
                            messagesBuilder.append("<called tool: ").append(functionToolCall.function().name()).append(" with params: ").append(functionToolCall.function().arguments()).append(">\n");
                        }
                    }

                    messagesBuilder.append("\"\n");
                }
                case "tool" -> {
                    ContentPart.TextContentPart textContentPart = content
                            .stream()
                            .filter((o) -> o instanceof ContentPart.TextContentPart)
                            .map((o) -> (ContentPart.TextContentPart) o)
                            .findFirst()
                            .orElseThrow();

                    messagesBuilder.append(textContentPart.text()).append("\"\n\n");
                }
            }

        });

        AiConfig.MemorizingSettings settings = config.get().memorizingSettings;

        CreateChatCompletionRequest.Builder builder = CreateChatCompletionRequest.newBuilder();

        builder.model(settings.model.model)
                .message(ChatMessage.systemMessage(settings.preset.prompt))
                .message(ChatMessage.userMessage(messagesBuilder.toString()))
                .temperature(settings.preset.temperature)
                .maxTokens(settings.preset.maxTokens)
                .topP(settings.preset.topP)
                .frequencyPenalty(settings.preset.frequencyPenalty)
                .presencePenalty(settings.preset.presencePenalty);

        CreateChatCompletionRequest chatCompletionRequest = builder.build();

        return servers.get().get(settings.serverIndexToUse).chatClient()
                .createChatCompletionAsync(chatCompletionRequest)
                .thenApply((r) -> {
                    usageManager.addUsage(chatId, GeneralUsageType.MEMORIZING, r.usage(), settings.model.inputPricePer1M, settings.model.outputPricePer1M, LocalDate.now());

                    return r;
                })
                .thenApply((r) -> r.choices().get(0).message().content());
    }

    protected CreateChatCompletionRequest createChatCompletionRequest(List<AiMessagesRecord> messages, AiPresetsRecord preset, AiModelsRecord modelRecord, String lastMemory) {
        CreateChatCompletionRequest.Builder builder = CreateChatCompletionRequest.newBuilder();

        builder.tools(Arrays.stream(modelRecord.getIncludedTools())
                .map(toolsMap::get)
                .toList());

        builder.messages(new ChatMessagesBuilder(messages, lastMemory).build())
                .model(modelRecord.getModel())
                .temperature(preset.getTemperature())
                .maxTokens(preset.getMaxTokens())
                .topP(preset.getTopP())
                .frequencyPenalty(preset.getFrequencyPenalty())
                .presencePenalty(preset.getPresencePenalty());

        return builder.build();
    }
}