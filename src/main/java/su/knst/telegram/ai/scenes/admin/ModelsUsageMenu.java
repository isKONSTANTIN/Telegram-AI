package su.knst.telegram.ai.scenes.admin;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import app.finwave.tat.utils.Pair;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.response.BaseResponse;
import io.github.stefanbratanov.jvm.openai.Usage;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.ChatsWhitelistRecord;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.menu.AskMenu;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ModelsUsageMenu extends MessageMenu<FlexListButtonsLayout> {
    protected int modelId = -1;
    protected LocalDate date;

    protected List<AiModelsRecord> models;
    protected List<Pair<Long, Usage>> usage;
    protected Usage totalUsage;

    protected AiModelsManager modelsManager;
    protected WhitelistManager whitelistManager;

    public ModelsUsageMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, AiModelsManager modelsManager, WhitelistManager whitelistManager) {
        super(scene, new FlexListButtonsLayout(4));

        this.modelsManager = modelsManager;
        this.whitelistManager = whitelistManager;

        layout.addButton(new InlineKeyboardButton("<"), (e) -> {
            fetchUsage(date.minusMonths(1), modelId);
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Select model"), 2, (e) -> {
            AskMenu askMenu = new AskMenu(scene);

            askMenu.setText("Selecting Model", "");

            for (AiModelsRecord record : models) {
                askMenu.addAnswer(new InlineKeyboardButton(record.getName()), (event) -> {
                    fetchUsage(date, record.getId());
                    apply();
                });
            }

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton(">"), (e) -> {
            fetchUsage(date.plusMonths(1), modelId);
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Refresh"), 4, (e) -> {
            fetchUsage();
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Back"), 4, backListener);
    }

    protected void fetchUsage(LocalDate date, int modelId) {
        date = date.withDayOfMonth(1);

        if (date.equals(this.date) && modelId == this.modelId)
            return;

        this.date = date;
        this.modelId = modelId;

        fetchUsage();
    }

    protected void fetchUsage() {
        this.usage = new ArrayList<>(modelsManager.getUsage(modelId, date));

        int completionUsage = 0;
        int promptUsage = 0;

        for (Pair<Long, Usage> u : usage) {
            completionUsage += u.second().completionTokens();
            promptUsage += u.second().promptTokens();
        }

        this.totalUsage = new Usage(completionUsage, promptUsage, completionUsage + promptUsage);

        this.usage.sort(Comparator.comparingInt(p -> -p.second().totalTokens()));
    }

    protected void appendBar(MessageBuilder builder, float percent, int size) {
        int filled = Math.round(percent * size);
        boolean lastFullFilled = filled - Math.round(percent * size * 10) / 10f < 0 || filled == Math.round(percent * size * 10) / 10f;

        builder.append("║");

        for (int i = 0; i < filled; i++)
            builder.append(i != filled - 1 || lastFullFilled ? "▓" : "▒");

        for (int i = 0; i < size - filled; i++)
            builder.append("░");

        builder.append("║");
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        if (modelId == -1) {
            models = modelsManager.getModels();

            fetchUsage(LocalDate.now(), models.get(0).getId());
        }

        MessageBuilder builder = MessageBuilder.create();

        builder.append("Model Usage: ").bold().append(modelsManager.getModel(modelId).orElseThrow().getName()).bold();

        builder.append(", " + date.toString());

        builder.gap().gap();

        for (Pair<Long, Usage> pair : usage) {
            Optional<ChatsWhitelistRecord> recordOptional = whitelistManager.getWhitelistRecord(pair.first());

            if (recordOptional.isPresent()) {
                ChatsWhitelistRecord record = recordOptional.get();
                builder.bold().append(pair.first() + " - " + record.getDescription()).bold().gap();
            }else {
                builder.bold().line(String.valueOf(pair.first())).bold();
            }

            Usage chatUsage = pair.second();

            builder.fixedWidth().append("Completion: ").fixedWidth();
            float percent = (float) chatUsage.completionTokens() / totalUsage.completionTokens();
            appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + chatUsage.completionTokens()).gap();

            builder.fixedWidth().append("Prompt:     ").fixedWidth();
            percent = (float) chatUsage.promptTokens() / totalUsage.promptTokens();
            appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + chatUsage.promptTokens())
                    .gap().gap();
        }

        if (usage.isEmpty())
            builder.line("Usage info not found");

        setMessage(builder.build());

        return super.apply();
    }
}
