package su.knst.telegram.ai.scenes.settings;

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
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.utils.MessageUtils;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.lang.LangWorker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UserUsageMenu extends MessageMenu<FlexListButtonsLayout> {
    protected AiModelsManager manager;
    protected EventListener<CallbackQueryEvent> backListener;
    protected LocalDate date;

    protected Map<Integer, AiModelsRecord> modelsMap;
    protected List<UsageWithCost> usages;

    protected long totalCompletion;
    protected long totalPrompt;
    protected BigDecimal totalCost;

    public UserUsageMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, AiModelsManager manager) {
        super(scene, new FlexListButtonsLayout(2));

        this.manager = manager;
        this.backListener = backListener;
    }

    protected void fetch() {
        if (date == null)
            date = LocalDate.now();

        totalCompletion = 0;
        totalPrompt = 0;
        totalCost = BigDecimal.ZERO;

        modelsMap = manager.getModels()
                .stream()
                .filter(AiModelsRecord::getEnabled)
                .collect(Collectors.toMap(AiModelsRecord::getId, v -> v));

        usages = manager.getUserUsage(scene.getChatHandler().getChatId(), date)
                .stream()
                .map((p) -> new UsageWithCost(p.second(), modelsMap.get(p.first())))
                .sorted()
                .toList();

        for (UsageWithCost usage : usages) {
            totalCompletion += usage.usage.completionTokens();
            totalPrompt += usage.usage.promptTokens();
            totalCost = totalCost.add(usage.totalCost);
        }

        totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);
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

        for (UsageWithCost usage : usages) {
            builder.bold().line(usage.model.getModel() + ", $" + usage.totalCost).bold();

            builder.fixedWidth().append("Completion: ").fixedWidth();
            float percent = (float) usage.usage.completionTokens() / totalCompletion;
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + usage.usage.completionTokens() + ", $" + usage.completionCost).gap();

            builder.fixedWidth().append("Prompt:     ").fixedWidth();
            percent = (float) usage.usage.promptTokens() / totalPrompt;
            MessageUtils.appendBar(builder, percent, 15);
            builder.append(" " + (Math.round(percent * 1000) / 10f) + "%, " + usage.usage.promptTokens() + ", $" + usage.promptCost)
                    .gap().gap();
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

    protected static class UsageWithCost implements Comparator<UsageWithCost> {
        public final Usage usage;
        public final AiModelsRecord model;
        public final BigDecimal totalCost;
        public final BigDecimal completionCost;
        public final BigDecimal promptCost;

        public UsageWithCost(Usage usage, AiModelsRecord model) {
            this.usage = usage;
            this.model = model;

            completionCost = model.getCompletionTokensCost()
                    .multiply(new BigDecimal(usage.completionTokens()))
                    .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

            promptCost = model.getPromptTokensCost()
                    .multiply(new BigDecimal(usage.promptTokens()))
                    .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

            totalCost = completionCost.add(promptCost);
        }

        @Override
        public int compare(UsageWithCost o1, UsageWithCost o2) {
            return o1.totalCost.compareTo(o2.totalCost);
        }
    }
}
