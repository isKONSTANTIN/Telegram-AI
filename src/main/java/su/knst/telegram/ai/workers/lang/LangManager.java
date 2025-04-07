package su.knst.telegram.ai.workers.lang;

import app.finwave.rct.config.ConfigNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import su.knst.telegram.ai.config.ConfigWorker;

import java.util.HashMap;

public class LangManager {
    private static LangManager instance;

    protected ConfigNode rootNode;
    protected HashMap<String, LangWorker> langWorkers = new HashMap<>();
    protected LangWorker defaultLangWorker;

    public LangManager(ConfigWorker configWorker) {
        instance = this;

        this.rootNode = configWorker.getLangRootNode();
        this.defaultLangWorker = new LangWorker(rootNode.node("en"));
    }

    public LangWorker getCachedWorker(String code) {
        if (code == null)
            return defaultLangWorker;

        code = code.toLowerCase();

        if (!rootNode.exists(code))
            return defaultLangWorker;

        return langWorkers.computeIfAbsent(code, (k) -> new LangWorker(rootNode.node(k)));
    }

    public static LangWorker lang(String code) {
        return instance.getCachedWorker(code);
    }
}
