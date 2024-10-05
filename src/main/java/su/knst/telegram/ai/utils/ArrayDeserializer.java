package su.knst.telegram.ai.utils;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArrayDeserializer implements JsonDeserializer<Map<String, String>> {
    @Override
    public Map<String, String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Map<String, String> resultMap = new HashMap<>();

        JsonObject jsonObject = json.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String key = entry.getKey();
            JsonElement valueElement = entry.getValue();

            if (valueElement.isJsonArray()) {
                StringBuilder b = new StringBuilder();
                List<JsonElement> array = valueElement.getAsJsonArray().asList();

                for (int i = 0; i < array.size(); i++) {
                    b.append(array.get(i).getAsString());
                    if (i < array.size() - 1)
                        b.append(",");
                }

                resultMap.put(key, b.toString());
            } else if (valueElement.isJsonPrimitive()) {
                resultMap.put(key, valueElement.getAsString());
            }
        }
        return resultMap;
    }
}