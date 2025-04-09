package su.knst.telegram.ai.utils.usage;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Objects;

public final class UserGeneralUsage implements Comparable<UserGeneralUsage> {
    private final BigDecimal cost;
    private final GeneralUsageType type;

    public UserGeneralUsage(BigDecimal cost, GeneralUsageType type) {
        this.cost = cost;
        this.type = type;
    }

    public BigDecimal cost() {
        return cost;
    }

    public GeneralUsageType type() {
        return type;
    }

    @Override
    public int compareTo(UserGeneralUsage o) {
        return cost.compareTo(o.cost);
    }
}
