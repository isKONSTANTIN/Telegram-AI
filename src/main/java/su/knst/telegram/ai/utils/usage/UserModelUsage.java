package su.knst.telegram.ai.utils.usage;

import io.github.stefanbratanov.jvm.openai.Usage;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.scenes.settings.UserUsageMenu;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class UserModelUsage implements Comparable<UserModelUsage> {
    public final Usage usage;
    public final AiModelsRecord model;
    public final BigDecimal totalCost;
    public final BigDecimal completionCost;
    public final BigDecimal promptCost;

    public UserModelUsage(Usage usage, AiModelsRecord model, BigDecimal totalCost, BigDecimal completionCost, BigDecimal promptCost) {
        this.usage = usage;
        this.model = model;
        this.totalCost = totalCost;
        this.completionCost = completionCost;
        this.promptCost = promptCost;
    }

    public UserModelUsage(Usage usage, AiModelsRecord model) {
        this.usage = usage;
        this.model = model;

        completionCost = (model != null ? model.getCompletionTokensCost() : BigDecimal.ZERO)
                .multiply(new BigDecimal(usage.completionTokens()))
                .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

        promptCost = (model != null ? model.getPromptTokensCost() : BigDecimal.ZERO)
                .multiply(new BigDecimal(usage.promptTokens()))
                .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

        totalCost = completionCost.add(promptCost);
    }

    @Override
    public int compareTo(UserModelUsage o) {
        return totalCost.compareTo(o.totalCost);
    }
}
