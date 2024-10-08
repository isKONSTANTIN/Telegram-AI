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
import su.knst.telegram.ai.scenes.main.MainScene;
import su.knst.telegram.ai.workers.AiWorker;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class AddPresetCommand extends AbstractCommand {
    protected ConfigWorker configWorker;
    protected AiWorker aiWorker;

    public AddPresetCommand(ConfigWorker configWorker, AiWorker aiWorker) {
        this.configWorker = configWorker;
        this.aiWorker = aiWorker;
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
