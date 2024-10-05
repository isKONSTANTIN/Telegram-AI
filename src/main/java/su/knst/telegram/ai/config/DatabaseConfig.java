package su.knst.telegram.ai.config;

import java.util.Optional;

public class DatabaseConfig {
    public String url = "jdbc:postgresql://postgres:5432/tg_ai";
    public String user = "tg_ai";
    public String password = Optional
            .ofNullable(System.getenv("DATABASE_PASSWORD"))
            .orElse("change_me");
}
