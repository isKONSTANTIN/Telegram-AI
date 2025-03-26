package su.knst.telegram.ai.workers.lang;

import app.finwave.rct.config.ConfigManager;
import app.finwave.rct.config.ConfigNode;
import app.finwave.rct.reactive.property.Property;
import app.finwave.rct.reactive.value.Value;
import app.finwave.tat.utils.Pair;

import java.io.IOException;

public class LangWorker {
    protected ConfigNode rootNode;

    public LangWorker(ConfigNode rootNode) {
        this.rootNode = rootNode;
    }

    protected Pair<ConfigNode, String> getNodeAndKey(String path) {
        String[] subpaths = path.split("\\.");

        if (subpaths.length == 0)
            throw new IllegalArgumentException();

        String key = subpaths[subpaths.length - 1];

        ConfigNode node = rootNode;

        for (int i = 0; i < subpaths.length - 1; i++) {
            String subpath = subpaths[i];
            node = node.node(subpath);
        }

        return new Pair<>(node, key);
    }

    public Value<String> getValue(String path, String defaultValue) {
        Pair<ConfigNode, String> nodeAndKey = getNodeAndKey(path);

        Property<String> result = nodeAndKey.first().getAsString(nodeAndKey.second());

        if (!nodeAndKey.first().exists(nodeAndKey.second()))
            result.set(defaultValue);

        return result;
    }

    public Value<String> getValue(String path) {
        Pair<ConfigNode, String> nodeAndKey = getNodeAndKey(path);

        return nodeAndKey.first().getAsString(nodeAndKey.second());
    }

    public String get(String path, String defaultValue) {
        return getValue(path, defaultValue).get();
    }
}
