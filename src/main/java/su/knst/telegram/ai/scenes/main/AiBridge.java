package su.knst.telegram.ai.scenes.main;

import app.finwave.scw.utils.gson.G;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.tat.utils.Pair;
import com.pengrad.telegrambot.model.reaction.ReactionTypeEmoji;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SetMessageReaction;
import com.pengrad.telegrambot.response.BaseResponse;
import io.github.stefanbratanov.jvm.openai.ChatCompletion;
import io.github.stefanbratanov.jvm.openai.ContentPart;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.utils.ContentMeta;
import su.knst.telegram.ai.utils.ContextMode;
import su.knst.telegram.ai.utils.functions.*;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.ContextUpdate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static su.knst.telegram.ai.utils.ContentPartParser.GSON;
import static su.knst.telegram.ai.utils.ContentPartParser.contentToJson;

public class AiBridge {
    protected MainScene scene;
    protected long chatId;

    protected AiWorker aiWorker;

    public AiBridge(MainScene scene, long chatId, AiWorker aiWorker) {
        this.scene = scene;
        this.chatId = chatId;

        this.aiWorker = aiWorker;
    }

    protected void contextUpdate(ContextUpdate update, int replyTo) throws ExecutionException, InterruptedException {
        ChatCompletion.Choice.Message message = update.newMessage();
        FunctionResult functionResult = update.functionResult();

        if (message != null) {
            ArrayList<Object> contents = new ArrayList<>();

            if (message.content() != null)
                contents.add(ContentPart.textContentPart(message.content()));

            if (message.toolCalls() != null && !message.toolCalls().isEmpty())
                contents.add(new ContentMeta(message.toolCalls().stream().map(G.GSON::toJsonTree).toList(), null));

            AiMessagesRecord record = aiWorker.getMessagesManager().pushMessage(update.contextId(), update.chatId(), "assistant", contentToJson(contents)).orElseThrow();

            if (message.content() != null)
                sendAndLinkContext(message.content(), record, replyTo).get();
        }

        if (functionResult != null) {
            if (functionResult instanceof FunctionImageResult imageResult) {
                AiMessagesRecord record = successCall(update);

                sendAndLinkContext(imageResult, record, replyTo).get();

                return;
            }

            if (functionResult instanceof FunctionReactionResult reactionResult) {
                String errorDescription = sendReaction(replyTo, reactionResult.emoji);

                if (errorDescription != null) {
                    aiWorker.getMessagesManager().pushMessage(update.contextId(), update.chatId(), "tool", contentToJson(List.of(
                            ContentPart.textContentPart("Failed: " + errorDescription),
                            new ContentMeta(null, update.callId())
                    )));

                    return;
                }

                successCall(update);

                return;
            }

            if (functionResult instanceof FunctionFileResult fileResult) {
                AiMessagesRecord record = aiWorker.getMessagesManager().pushMessage(update.contextId(), update.chatId(), "tool", contentToJson(List.of(
                        ContentPart.textContentPart("Success"),
                        new ContentMeta(null, update.callId())
                ))).orElseThrow();

                sendFileAndLinkContext(fileResult.content.getBytes(), fileResult.filename, record, replyTo).get();

                return;
            }

            aiWorker.getMessagesManager().pushMessage(update.contextId(), update.chatId(), "tool", contentToJson(List.of(
                    new ContentPart.TextContentPart(GSON.toJson(functionResult)),
                    new ContentMeta(null, update.callId())
            )));
        }
    }

    protected AiMessagesRecord successCall(ContextUpdate update) {
        return aiWorker.getMessagesManager().pushMessage(update.contextId(), update.chatId(), "tool", contentToJson(List.of(
                ContentPart.textContentPart("Success"),
                new ContentMeta(null, update.callId())
        ))).orElseThrow();
    }

    protected String sendReaction(int messageId, String emoji) {
        try {
            BaseResponse response = scene.getChatHandler().getCore().execute(new SetMessageReaction(
                    chatId,
                    messageId,
                    new ReactionTypeEmoji(emoji))
            ).get();

            return response.isOk() ? null : response.description();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    protected CompletableFuture<?> sendAndLinkContext(FunctionImageResult imageResult, AiMessagesRecord record, int replyTo) {
        CompletableFuture<File> future = FileDownloader.downloadFile(imageResult.url);

        return future.thenApply((f) -> {
            try {
                ContextMode contextMode = ContextMode.values()[scene.preferences.getContextsMode()];
                SendPhoto request = new SendPhoto(chatId, f);

                if (contextMode == ContextMode.MULTI_REPLY)
                    request.replyToMessageId(replyTo);

                return scene.getChatHandler().getCore().execute(request).whenComplete((r, t) -> {
                    try {
                        Files.deleteIfExists(f.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (t != null || !r.isOk())
                        return;

                    aiWorker.getMessagesManager().linkTelegramMessage(record.getId(), r.message().messageId());
                    scene.lastContext = record.getAiContext();
                }).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected CompletableFuture<?> sendFileAndLinkContext(byte[] bytes, String filename, AiMessagesRecord aiMessage, int replyTo) {
        ContextMode contextMode = ContextMode.values()[scene.preferences.getContextsMode()];
        Path tmpFile;

        try {
            tmpFile = Files.createTempFile(Path.of("/","tmp","/"), "knst_ai_response_",".txt");
            Files.write(tmpFile, bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SendDocument request = new SendDocument(chatId, tmpFile.toFile()).fileName(filename != null ? filename : tmpFile.toFile().getName());

        if (contextMode == ContextMode.MULTI_REPLY)
            request.replyToMessageId(replyTo);

        return scene.getChatHandler().getCore()
                .execute(request)
                .whenComplete((r, t) -> {
                    try {
                        Files.deleteIfExists(tmpFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (t != null || !r.isOk())
                        return;

                    aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.getId(), r.message().messageId());
                    scene.lastContext = aiMessage.getAiContext();
                });
    }

    protected CompletableFuture<?> sendAndLinkContext(String answer, AiMessagesRecord aiMessage, int replyTo) {
        CompletableFuture<?> future;
        byte[] bytes = answer.getBytes();

        ContextMode contextMode = ContextMode.values()[scene.preferences.getContextsMode()];

        if (bytes.length >= 1024 * 4) {
            String filename;

            try {
                filename = aiWorker.generateFilename(answer).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            future = sendFileAndLinkContext(bytes, filename, aiMessage, replyTo);
        }else {
            MessageBuilder messageBuilder = MessageBuilder.create(answer.isBlank() ? "(AI is silent)" : answer);

            if (contextMode == ContextMode.MULTI_REPLY)
                messageBuilder.setReplyTo(replyTo);

            messageBuilder.setParseMode(ParseMode.HTML);

            future = scene.getChatHandler().sendMessage(messageBuilder.build()).whenComplete((r, t) -> {
                if (t != null || !r.isOk())
                    return;

                aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.getId(), r.message().messageId());
                scene.lastContext = aiMessage.getAiContext();
            });
        }

        return future;
    }
}
