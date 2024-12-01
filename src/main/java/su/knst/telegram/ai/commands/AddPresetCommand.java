package su.knst.telegram.ai.commands;

import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.handlers.command.AbstractCommand;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.scenes.main.MainScene;
import su.knst.telegram.ai.utils.UserPermission;
import su.knst.telegram.ai.workers.AiWorker;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class AddPresetCommand extends AbstractCommand {
    protected ConfigWorker configWorker;
    protected AiWorker aiWorker;
    protected WhitelistManager whitelistManager;

    public AddPresetCommand(ConfigWorker configWorker, AiWorker aiWorker, WhitelistManager whitelistManager) {
        this.configWorker = configWorker;
        this.aiWorker = aiWorker;
        this.whitelistManager = whitelistManager;
    }

    @Override
    public String name() {
        return "add_preset";
    }

    @Override
    public String description() {
        return "Create new preset for AI behavior";
    }

    @Override
    public String commandArgsTips() {
        return "<tag> <prompt>";
    }

    @Override
    public void run(String[] strings, NewMessageEvent newMessageEvent) {
        if (newMessageEvent.data.chat().type() != Chat.Type.Private) {
            UserPermission permission = whitelistManager.getPermission(newMessageEvent.data.from().id());

            if (permission != UserPermission.SUPER_ADMIN && permission != UserPermission.ADMIN) {
                ChatMember.Status status;

                try {
                    status = chatHandler.getCore()
                            .execute(new GetChatMember(newMessageEvent.data.chat().id(), newMessageEvent.data.from().id()))
                            .thenApply(GetChatMemberResponse::chatMember)
                            .thenApply(ChatMember::status)
                            .get();
                } catch (InterruptedException | ExecutionException e) {
                    return;
                }

                if (status != ChatMember.Status.creator && status != ChatMember.Status.administrator)
                    return;
            }
        }

        chatHandler.deleteMessage(newMessageEvent.data.messageId());

        if (strings.length < 2) {
            chatHandler.sendMessage(MessageBuilder.text("Usage: <tag> <prompt>"));

            return;
        }

        AiConfig.Preset defaultPreset = configWorker.ai.defaultUserPreset;
        AiModelsRecord modelsRecord = aiWorker.getModelsManager().getModels().get(0);

        var preset = aiWorker.getPresetsManager().addPreset(
                newMessageEvent.chatId,
                modelsRecord.getId(),
                defaultPreset.temperature,
                defaultPreset.topP,
                defaultPreset.frequencyPenalty,
                defaultPreset.presencePenalty,
                defaultPreset.maxTokens,
                String.join(" ", Arrays.copyOfRange(strings, 1, strings.length)),
                strings[0]
        );

        if (preset.isEmpty()) {
            chatHandler.sendMessage(MessageBuilder.text("Failed to add new preset"));

            return;
        }

        chatHandler.sendMessage(MessageBuilder.text("Preset added"));
    }
}
