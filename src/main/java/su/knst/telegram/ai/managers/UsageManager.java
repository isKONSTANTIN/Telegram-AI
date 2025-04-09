package su.knst.telegram.ai.managers;

import app.finwave.tat.utils.Pair;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.stefanbratanov.jvm.openai.Usage;
import su.knst.telegram.ai.database.DatabaseWorker;
import su.knst.telegram.ai.database.UsageDatabase;
import su.knst.telegram.ai.utils.usage.GeneralUsageType;
import su.knst.telegram.ai.utils.usage.UserGeneralUsage;
import su.knst.telegram.ai.utils.usage.UserModelUsage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Singleton
public class UsageManager {
    protected UsageDatabase database;
    protected AiModelsManager modelsManager;

    @Inject
    public UsageManager(DatabaseWorker worker, AiModelsManager modelsManager) {
        this.database = worker.get(UsageDatabase.class);
        this.modelsManager = modelsManager;
    }

    public Usage addUsage(int modelId, long chatId, Usage usage) {
        LocalDate now = LocalDate.now().withDayOfMonth(1);

        return database.addUsage(modelId, chatId, now, usage);
    }

    public List<Pair<Long, Usage>> getModelUsage(int modelId, LocalDate date) {
        return database.getModelUsage(modelId, date);
    }

    public List<UserModelUsage> getUserModelsUsage(long chatId, LocalDate date) {
        return database.getModelsUsage(chatId, date)
                .stream()
                .map((p) -> new UserModelUsage(p.second(), modelsManager.getModel(p.first()).orElse(null)))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public BigDecimal addUsage(long chatId, GeneralUsageType type, BigDecimal cost, LocalDate date) {
        LocalDate now = LocalDate.now().withDayOfMonth(1);

        return database.addUsage(chatId, type, cost, date);
    }

    public BigDecimal addUsage(long chatId, GeneralUsageType type, Usage usage, double inputPrice, double outputPrice, LocalDate date) {
        BigDecimal inputCost = BigDecimal.valueOf(inputPrice)
                .multiply(BigDecimal.valueOf(usage.completionTokens()))
                .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

        BigDecimal outputCost = BigDecimal.valueOf(outputPrice)
                .multiply(BigDecimal.valueOf(usage.promptTokens()))
                .divide(new BigDecimal("1000000"), 2, RoundingMode.HALF_UP);

        return addUsage(chatId, type, inputCost.add(outputCost), date);
    }

    public List<Pair<Long, BigDecimal>> getGeneralUsage(GeneralUsageType type, LocalDate date) {
        return database.getGeneralUsage(type, date);
    }

    public List<UserGeneralUsage> getUserGeneralsUsage(long chatId, LocalDate date) {
        return database.getGeneralsUsage(chatId, date)
                .stream()
                .map((p) -> new UserGeneralUsage(p.second(), p.first()))
                .sorted(Comparator.reverseOrder())
                .toList();
    }
}
