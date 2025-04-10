package su.knst.telegram.ai.scenes.main;

import app.finwave.tat.event.chat.EditedMessageEvent;
import app.finwave.tat.event.chat.MessageReactionEvent;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.tat.utils.Pair;
import com.google.common.cache.LoadingCache;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.model.message.origin.MessageOrigin;
import com.pengrad.telegrambot.model.message.origin.MessageOriginChannel;
import com.pengrad.telegrambot.model.message.origin.MessageOriginUser;
import com.pengrad.telegrambot.model.reaction.ReactionTypeEmoji;
import com.pengrad.telegrambot.model.request.ChatAction;
import com.pengrad.telegrambot.request.GetFile;
import io.github.stefanbratanov.jvm.openai.ContentPart;
import su.knst.telegram.ai.Main;
import su.knst.telegram.ai.jooq.tables.records.AiContextsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiMessagesRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.utils.CacheHandyBuilder;
import su.knst.telegram.ai.utils.ContextMode;
import su.knst.telegram.ai.utils.MentionMode;
import su.knst.telegram.ai.utils.functions.FileDownloader;
import su.knst.telegram.ai.utils.parsers.OggToWav;
import su.knst.telegram.ai.utils.parsers.TextConverters;
import su.knst.telegram.ai.workers.AiWorker;
import ws.schild.jave.EncoderException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class UserBridge {
    protected MainScene scene;
    protected AlbumHandler albumHandler;

    protected AiWorker aiWorker;
    protected long chatId;

    public HashMap<Long, ReentrantLock> contextsLocks = new HashMap<>();

    public UserBridge(MainScene scene, long chatId, AiWorker aiWorker) {
        this.scene = scene;
        this.chatId = chatId;

        this.aiWorker = aiWorker;

        this.albumHandler = new AlbumHandler(this::newAlbum);
    }

    protected ReentrantLock getLock(long contextId) {
        return contextsLocks.computeIfAbsent(contextId, k -> new ReentrantLock());
    }

    public void editedMessage(EditedMessageEvent event) {
        Optional<AiMessagesRecord> message = aiWorker.getMessagesManager().getFirstTelegramMessage(chatId, event.data.messageId());

        if (message.isEmpty())
            return;

        ReentrantLock lock = getLock(message.get().getAiContext());
        lock.lock();

        try {
            List<AiMessagesRecord> deleted = aiWorker.getMessagesManager().deleteAllAfter(chatId, message.get().getId());

            deleted.stream()
                    .filter(m -> m.getMessageId() != -1)
                    .filter(m -> m.getMessageId() != event.data.messageId().intValue())
                    .map(m -> m.getMessageId().intValue())
                    .forEach(scene.getChatHandler()::deleteMessage);
        }finally {
            lock.unlock();
        }

        newMessage(event.data);
    }

    public void newMessage(NewMessageEvent event) {
        if (event.data.mediaGroupId() != null) {
            albumHandler.handle(event);

            return;
        }

        albumHandler.waitAll();

        newMessage(event.data);
    }

    protected void newMessage(Message message) {
        String text = null;

        if (message.text() != null)
            text = message.text();
        else if (message.caption() != null)
            text = message.caption();

        Pair<Boolean, String> needHandle = needHandle(message, text);

        if (!needHandle.first())
            return;

        text = needHandle.second();

        AiContextsRecord context = findOrInitContext(message, text);
        if (context == null) {
            scene.getChatHandler().sendMessage(MessageBuilder.text("Fail to find and init context"));

            return;
        }

        ReentrantLock lock = getLock(context.getId());
        lock.lock();

        try {
            Pair<AiContextsRecord, String> updatedContext = updateContextPreset(text, context);

            text = updatedContext.second();
            scene.lastContext = updatedContext.first().getId();

            if (message.photo() != null) {
                PhotoSize[] photos = message.photo();

                processPhoto(photos[photos.length - 1].fileId(), message, scene.lastContext);
            }

            if (message.sticker() != null && !message.sticker().isAnimated() && !message.sticker().isVideo()) {
                processPhoto(message.sticker().fileId(), message, scene.lastContext);
            }

            if (message.document() != null)
                processDocument(message.document(), message, scene.lastContext);

            if (text != null)
                processText(text, message, scene.lastContext);

            if (message.voice() != null) {
                processVoice(message.voice(), message, scene.lastContext);
            }
        }finally {
            lock.unlock();
        }
    }

    protected Pair<AiContextsRecord, String> updateContextPreset(String text, AiContextsRecord context) {
        Pair<Optional<String>, String> tagResult = findTag(text);
        Optional<String> tag = tagResult.first();

        Optional<AiPresetsRecord> newPreset = tag.flatMap(this::getPreset);

        if (newPreset.isPresent() && !context.getLastPresetId().equals(newPreset.get().getId())) {
            context = aiWorker
                    .updateContext(context.getId(), newPreset.get().getId())
                    .orElseThrow();
        }

        return Pair.of(context, tagResult.second());
    }

    protected AiContextsRecord findOrInitContext(Message message, String text) {
        Optional<AiContextsRecord> optionalContext = findContext(message);
        AiContextsRecord context = null;

        if (optionalContext.isEmpty()) {
            List<AiPresetsRecord> presets = aiWorker.getPresetsManager().getPresets(chatId);

            if (presets.isEmpty()) {
                scene.getChatHandler().sendMessage(MessageBuilder.text("Presets is empty"));

                return null;
            }

            Pair<Optional<String>, String> tagResult = findTag(text);
            Optional<String> tag = tagResult.first();

            AiPresetsRecord preset = tag.flatMap(this::getPreset)
                    .orElseGet(() ->
                            scene.preferencesManager.getPreferences(chatId)
                                    .map(ChatsPreferencesRecord::getDefaultPreset)
                                    .flatMap((presetId) -> presets.stream().filter((p) -> p.getId().equals(presetId)).findFirst())
                                    .orElse(presets.get(0))
                    );

            context = aiWorker.initContext(chatId, preset.getId()).orElse(null);

            if (message.replyToMessage() != null && context != null) {
                Message reply = message.replyToMessage();

                if (reply.photo() != null) {
                    PhotoSize[] photos = reply.photo();

                    processPhoto(photos[photos.length - 1].fileId(), reply, context.getId());
                }

                if (reply.sticker() != null && !message.sticker().isAnimated() && !message.sticker().isVideo()) {
                    processPhoto(message.sticker().fileId(), message, scene.lastContext);
                }

                if (reply.document() != null)
                    processDocument(reply.document(), reply, context.getId());

                if (reply.text() != null) {
                    Optional<AiMessagesRecord> aiMessage = aiWorker.getMessagesManager().pushMessage(context.getId(), chatId, "user", List.of(
                            ContentPart.textContentPart(reply.text())
                    ));

                    if (aiMessage.isPresent())
                        aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), reply.messageId());
                }
            }
        }else {
            context = optionalContext.get();
        }

        return context;
    }

    protected Optional<AiContextsRecord> findContext(Message newMessage) {
        Optional<AiContextsRecord> result = Optional.empty();

        ContextMode contextMode = ContextMode.values()[scene.preferences.getContextsMode()];

        Message replyMessage = newMessage.replyToMessage();

        if (replyMessage != null) {
            result = aiWorker.getMessagesManager()
                    .getMessage(replyMessage.chat().id(), replyMessage.messageId())
                    .flatMap(m -> aiWorker.getContextManager().getContext(m.getAiContext()));
        }else if ((newMessage.forwardOrigin() != null || contextMode == ContextMode.SINGLE) && scene.lastContext != -1) {
            result = aiWorker.getContextManager().getContext(scene.lastContext);
        }


        return result;
    }

    protected Optional<AiPresetsRecord> getPreset(String tag) {
        List<AiPresetsRecord> presets = aiWorker.getPresetsManager().getPresets(chatId);

        if (presets.isEmpty() || tag == null || tag.isBlank())
            return Optional.empty();

        return presets.stream()
                .filter(p -> p.getTag().equals(tag))
                .findAny();
    }

    protected void processText(String text, Message message, long contextId) {
        if (message.forwardOrigin() != null) {
            MessageOrigin forwarded = message.forwardOrigin();

            if (forwarded instanceof MessageOriginUser user) {
                text = "Forwarded from " + user.senderUser().firstName() + " (@" + user.senderUser().username() + "): " + text;
            }else if (forwarded instanceof MessageOriginChannel channel) {
                text = "Forwarded from " + channel.chat().firstName() + " (@" + channel.chat().username() + "): " + text;
            }else {
                text = "Forwarded message: " + text;
            }

            Optional<AiMessagesRecord> aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                    ContentPart.textContentPart(text)
            ));

            if (aiMessage.isPresent())
                aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), message.messageId());

            return;
        }

        if (message.quote() != null) {
            Optional<AiMessagesRecord> aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                    ContentPart.textContentPart("User's quote: " + message.quote().text())
            ));

            if (aiMessage.isPresent())
                aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), message.messageId());
        }

        if (message.chat().type() != Chat.Type.Private)
            text = message.from().username() + " says: " + text;

        Optional<AiMessagesRecord> aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "user", List.of(
                ContentPart.textContentPart(text)
        ));

        if (aiMessage.isPresent())
            aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), message.messageId());

        try {
            scene.askAndAnswer(contextId, message.messageId()).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    protected void processPhoto(String fileId, Message message, long contextId) {
        try {
            scene.getChatHandler().getCore().execute(new GetFile(fileId)).thenAccept(r -> {
                String path = r.file().filePath();

                Optional<AiMessagesRecord> aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "user", List.of(
                        ContentPart.imageUrlContentPart("https://api.telegram.org/file/bot" + Main.getBotToken() + "/"+ path)
                ));

                if (aiMessage.isPresent())
                    aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), message.messageId());
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();

            scene.getChatHandler().sendMessage(MessageBuilder.text("Processing photo failed"));
        }
    }

    protected void processDocument(Document document, Message message, long contextId) {
        try {
            scene.getChatHandler().getCore().execute(new GetFile(document.fileId())).thenAccept(r -> {
                String path = r.file().filePath();
                String fileUrl = "https://api.telegram.org/file/bot" + Main.getBotToken() + "/" + path;
                String mimeType = document.mimeType();
                Optional<AiMessagesRecord> aiMessage = Optional.empty();

                try {
                    if (mimeType.startsWith("image/"))
                        aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "user", List.of(
                                ContentPart.imageUrlContentPart(fileUrl)
                        ));
                    else if (mimeType.equals("application/pdf")) {
                        String pdfText = FileDownloader.downloadFile(fileUrl).thenApply(TextConverters::parsePdf).get().orElseThrow();

                        aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                                ContentPart.textContentPart("User uploaded .pdf file with text: " + pdfText)
                        ));
                    } else if (mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                        String docxText = FileDownloader.downloadFile(fileUrl).thenApply(TextConverters::docx2markdown).get().orElseThrow();

                        aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                                ContentPart.textContentPart("User uploaded .docx file with text: " + docxText)
                        ));
                    } else if (mimeType.equals("text/html")) {
                        String pageText = FileDownloader.downloadFile(fileUrl).thenApply(TextConverters::parseHtml).get().orElseThrow();

                        aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                                ContentPart.textContentPart("User uploaded .html file with text: " + pageText)
                        ));
                    }

                    if (aiMessage.isEmpty()) {
                        String textFile = FileDownloader.downloadFileAsString(fileUrl).get();

                        if (FileDownloader.isReadable(textFile))
                            aiMessage = aiWorker.getMessagesManager().pushMessage(contextId, chatId, "system", List.of(
                                    ContentPart.textContentPart("User uploaded " + mimeType + " file: " + textFile)
                            ));
                    }
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }

                if (aiMessage.isPresent())
                    aiWorker.getMessagesManager().linkTelegramMessage(aiMessage.get().getId(), message.messageId());
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            scene.getChatHandler().sendMessage(MessageBuilder.text("Unsupported document type"));
        }
    }

    protected void processVoice(Voice audio, Message message, long contextId) {
        CompletableFuture<String> textFuture = scene.getChatHandler().getCore().execute(new GetFile(audio.fileId())).thenCompose(r -> {
            String path = r.file().filePath();
            String fileUrl = "https://api.telegram.org/file/bot" + Main.getBotToken() + "/" + path;

             return FileDownloader.downloadFile(fileUrl).thenApply((ogg) -> {
                 try {
                     return OggToWav.ogg2Wav(ogg);
                 } catch (IOException | EncoderException e) {
                     throw new RuntimeException(e);
                 }finally {
                     ogg.delete();
                 }
             }).thenCompose((f) -> {
                try {
                    return aiWorker.speech2text(f.toPath(), chatId, audio.duration());
                }finally {
                    f.delete();
                }
            });
        });

        scene.getChatHandler().setActionUntil(textFuture, ChatAction.typing);

        String text;
        try {
            text = textFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            scene.getChatHandler().sendMessage(MessageBuilder.text("Failed to process voice"));

            return;
        }

        processText(text, message, contextId);
    }

    protected Pair<Optional<String>, String> findTag(String text) {
        if (text == null)
            return Pair.of(Optional.empty(), null);

        String result = null;

        String[] words = text.trim().split(" ");

        if (words.length > 0 && words[0].startsWith("#")) {
            text = String.join(" ", Arrays.copyOfRange(words, 1, words.length));
            result = words[0].substring(1);
        }

        return Pair.of(Optional.ofNullable(result), text);
    }

    protected void reaction(MessageReactionEvent event) {
        List<ReactionTypeEmoji> reactions = Arrays.stream(event.data.newReaction())
                .filter(r -> r.type().equals("emoji"))
                .map((r) -> (ReactionTypeEmoji)r)
                .toList();

        if (reactions.isEmpty())
            return;

        Optional<AiContextsRecord> context = aiWorker.getMessagesManager()
                .getMessage(chatId, event.data.messageId())
                .flatMap(m -> aiWorker.getContextManager().getContext(m.getAiContext()));

        if (context.isEmpty())
            return;

        List<AiMessagesRecord> linkedMessages = aiWorker.getMessagesManager()
                .getMessages(context.get().getId())
                .stream()
                .filter(m -> m.getMessageId() != -1)
                .toList();

        AiMessagesRecord lastMessage = linkedMessages.get(linkedMessages.size() - 1);

        if (lastMessage.getMessageId() != event.data.messageId().longValue())
            return;

        String newReactions = reactions.stream()
                .map(ReactionTypeEmoji::emoji)
                .collect(Collectors.joining(" "));

        ReentrantLock lock = getLock(context.get().getId());
        lock.lock();

        try {
            aiWorker.getMessagesManager().pushMessage(context.get().getId(), chatId, "user", List.of(
                    ContentPart.textContentPart(newReactions)
            ));

            AiMessagesRecord lastUserMessage = lastMessage;

            List<AiMessagesRecord> userMessages = linkedMessages.stream()
                    .filter(m -> m.getRole().equals("user"))
                    .toList();

            if (!userMessages.isEmpty())
                lastUserMessage = userMessages.get(userMessages.size() - 1);

            scene.askAndAnswer(context.get().getId(), lastUserMessage.getMessageId().intValue()).get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    protected Pair<Boolean, String> needHandle(Message message, String text) {
        if (message.chat().type() == Chat.Type.Private || MentionMode.values()[scene.preferences.getMentionMode()] == MentionMode.WITHOUT_MENTION)
            return Pair.of(true, text);

        User me = scene.getChatHandler().getCore().getMe().orElseThrow();
        Message reply = message.replyToMessage();

        boolean startWithMe = text != null && text.startsWith("@" + me.username());
        boolean replyOnMe = reply != null && reply.from().username().equals(me.username());

        if (!startWithMe && !replyOnMe)
            return Pair.of(false, text);

        if (startWithMe)
            text = text.replace("@" + me.username() + " ", "");

        return Pair.of(true, text);
    }

    protected void newAlbum(AlbumData albumData) {
        List<NewMessageEvent> events = albumData.getEvents();

        NewMessageEvent firstEvent = events.get(0);
        Pair<Boolean, String> needHandle = needHandle(firstEvent.data, albumData.caption);

        if (!needHandle.first())
            return;

        String text = needHandle.second();

        AiContextsRecord context = findOrInitContext(firstEvent.data, text);
        if (context == null) {
            scene.getChatHandler().sendMessage(MessageBuilder.text("Fail to find and init context"));

            return;
        }

        ReentrantLock lock = getLock(context.getId());
        lock.lock();

        try {
            Pair<AiContextsRecord, String> updatedContext = updateContextPreset(text, context);

            text = updatedContext.second();
            scene.lastContext = updatedContext.first().getId();

            for (NewMessageEvent event : events) {
                PhotoSize[] photos = event.data.photo();

                processPhoto(photos[photos.length - 1].fileId(), event.data, scene.lastContext);
            }

            if (text != null)
                processText(text, firstEvent.data, scene.lastContext);
        }finally {
            lock.unlock();
        }
    }
}
