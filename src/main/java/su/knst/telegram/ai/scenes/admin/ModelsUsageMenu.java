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
import su.knst.telegram.ai.jooq.tables.records.ChatsRecord;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.UsageManager;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.MessageUtils;
import su.knst.telegram.ai.utils.menu.AskMenu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static su.knst.telegram.ai.utils.MessageUtils.appendBar;

public class ModelsUsageMenu extends MessageMenu<FlexListButtonsLayout> {
    protected int modelId = -1;
    protected AiModelsRecord modelRecord;
    protected LocalDate date;

    protected List<AiModelsRecord> models;
    protected List<Pair<Long, Usage>> usage;
    protected Usage totalUsage;

    protected AiModelsManager modelsManager;
    protected UsageManager usageManager;

    protected WhitelistManager whitelistManager;

    public ModelsUsageMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, AiModelsManager modelsManager, UsageManager usageManager, WhitelistManager whitelistManager) {
        super(scene, new FlexListButtonsLayout(4));

        this.modelsManager = modelsManager;
        this.whitelistManager = whitelistManager;
        this.usageManager = usageManager;

        layout.addButton(new InlineKeyboardButton("<"), (e) -> {
            fetchUsage(date.minusMonths(1), modelRecord);
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Select model"), 2, (e) -> {
            AskMenu askMenu = new AskMenu(scene);

            askMenu.setText("Selecting Model", "");

            for (AiModelsRecord record : models) {
                askMenu.addAnswer(new InlineKeyboardButton(record.getName()), (event) -> {
                    fetchUsage(date, record);
                    apply();
                });
            }

            askMenu.setResultFunction((s) -> {
                apply();

                return true;
            });

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton(">"), (e) -> {
            fetchUsage(date.plusMonths(1), modelRecord);
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Refresh"), 4, (e) -> {
            fetchUsage();
            apply();
        });

        layout.addButton(new InlineKeyboardButton("Back"), 4, backListener);
    }

    protected void fetchUsage(LocalDate date, AiModelsRecord model) {
        date = date.withDayOfMonth(1);

        if (date.equals(this.date) && model.getId() == this.modelId)
            return;

        this.date = date;
        this.modelId = model.getId();
        this.modelRecord = model;

        fetchUsage();
    }

    protected void fetchUsage() {
        this.usage = new ArrayList<>(usageManager.getModelUsage(modelId, date));

        int completionUsage = 0;
        int promptUsage = 0;

        for (Pair<Long, Usage> u : usage) {
            completionUsage += u.second().completionTokens();
            promptUsage += u.second().promptTokens();
        }

        this.totalUsage = new Usage(completionUsage, promptUsage, completionUsage + promptUsage);

        this.usage.sort(Comparator.comparingInt(p -> -p.second().totalTokens()));
    }

    public void refresh() {
        modelId = -1;
        models = modelsManager.getModels();
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        if (modelId == -1) {
            models = modelsManager.getModels();

            fetchUsage(LocalDate.now(), models.get(0));
        }

        MessageBuilder builder = MessageBuilder.create();

        builder.append("Model Usage: ").bold().append(modelsManager.getModel(modelId).orElseThrow().getName()).bold();

        builder.append(", " + date.toString());

        builder.gap().gap();

        for (Pair<Long, Usage> pair : usage) {
            Optional<ChatsRecord> recordOptional = whitelistManager.getWhitelistRecord(pair.first());

            Usage chatUsage = pair.second();

            BigDecimal completionCost = modelRecord.getCompletionTokensCost()
                    .multiply(new BigDecimal(chatUsage.completionTokens()))
                    .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

            BigDecimal promptCost = modelRecord.getPromptTokensCost()
                    .multiply(new BigDecimal(chatUsage.promptTokens()))
                    .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

            BigDecimal total = completionCost.add(promptCost);

            if (recordOptional.isPresent()) {
                ChatsRecord record = recordOptional.get();
                builder.bold().append(pair.first() + " - " + record.getDescription() + ", $" + total).bold().gap();
            }else {
                builder.bold().line(pair.first() + ", $" + total).bold();
            }

            builder.fixedWidth().append("Completion: ").fixedWidth();
            float percent = (float) chatUsage.completionTokens() / totalUsage.completionTokens();
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + chatUsage.completionTokens() + ", $" + completionCost).gap();

            builder.fixedWidth().append("Prompt:     ").fixedWidth();
            percent = (float) chatUsage.promptTokens() / totalUsage.promptTokens();
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + chatUsage.promptTokens() + ", $" + promptCost)
                    .gap().gap();
        }

        if (usage.isEmpty())
            builder.line("Usage info not found");

        setMessage(builder.build());

        return super.apply();
    }
}
