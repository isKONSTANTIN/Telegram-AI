package su.knst.telegram.ai.scenes.settings;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.response.BaseResponse;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.UsageManager;
import su.knst.telegram.ai.utils.MessageUtils;
import su.knst.telegram.ai.utils.usage.UserGeneralUsage;
import su.knst.telegram.ai.utils.usage.UserModelUsage;
import su.knst.telegram.ai.workers.lang.LangWorker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UserUsageMenu extends MessageMenu<FlexListButtonsLayout> {
    protected UsageManager manager;

    protected EventListener<CallbackQueryEvent> backListener;
    protected LocalDate date;

    protected List<UserModelUsage> usages;
    protected List<UserGeneralUsage> generalUsages;

    protected long totalCompletion;
    protected long totalPrompt;
    protected BigDecimal totalCost;
    protected BigDecimal totalGeneralCost;

    public UserUsageMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, UsageManager manager) {
        super(scene, new FlexListButtonsLayout(2));

        this.manager = manager;
        this.backListener = backListener;
    }

    protected void fetch() {
        if (date == null)
            date = LocalDate.now();

        long chatId = scene.getChatHandler().getChatId();

        totalCompletion = 0;
        totalPrompt = 0;
        totalCost = BigDecimal.ZERO;
        totalGeneralCost = BigDecimal.ZERO;

        usages = manager.getUserModelsUsage(chatId, date);
        generalUsages = manager.getUserGeneralsUsage(chatId, date);

        for (UserModelUsage usage : usages) {
            totalCompletion += usage.usage.completionTokens();
            totalPrompt += usage.usage.promptTokens();
            totalCost = totalCost.add(usage.totalCost);
        }

        for (UserGeneralUsage usage : generalUsages) {
            totalCost = totalCost.add(usage.cost());
            totalGeneralCost = totalGeneralCost.add(usage.cost());
        }

        totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);
        totalGeneralCost = totalGeneralCost.setScale(2, RoundingMode.HALF_UP);
    }

    public CompletableFuture<? extends BaseResponse> applyWithReset() {
        date = LocalDate.now();

        return apply();
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        layout.removeAll();

        LangWorker lang = ((ChatHandler) scene.getChatHandler()).getLang().get();
        fetch();

        MessageBuilder builder = MessageBuilder.create();

        builder.line(lang.get("scenes.settings.modelsUsage.menuInfo", "Models Usage"));
        builder.gap();
        builder.line(lang.get("scenes.settings.modelsUsage.totalCost", "Total cost: $%s").formatted(totalCost) + ", " + date.toString());

        builder.gap();

        for (UserModelUsage usage : usages) {
            builder.bold().line(usage.model.getModel() + ", $" + usage.totalCost).bold();

            builder.fixedWidth().append("Completion: ").fixedWidth();
            float percent = (float) usage.usage.completionTokens() / totalCompletion;
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + usage.usage.completionTokens() + ", $" + usage.completionCost.setScale(2, RoundingMode.HALF_UP)).gap();

            builder.fixedWidth().append("Prompt:     ").fixedWidth();
            percent = (float) usage.usage.promptTokens() / totalPrompt;
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + usage.usage.promptTokens() + ", $" + usage.promptCost.setScale(2, RoundingMode.HALF_UP))
                    .gap().gap();
        }

        builder.bold().line("Misc: $" + totalGeneralCost.toString()).bold();

        for (UserGeneralUsage usage : generalUsages) {
            builder.fixedWidth().append(usage.type().toString() + " ").fixedWidth();

            float percent = Math.min(usage.cost().floatValue() / totalGeneralCost.floatValue(), 1);
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, $" + usage.cost().setScale(2, RoundingMode.HALF_UP)).gap();
        }

        setMessage(builder.build());

        layout.addButton(new InlineKeyboardButton("<"), (e) -> {
            date = date.minusMonths(1);
            apply();
        });

        layout.addButton(new InlineKeyboardButton(">"), (e) -> {
            date = date.plusMonths(1);
            apply();
        });

        layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.modelsUsage.buttons.back", "Back")), 2, backListener);

        return super.apply();
    }
}
