package su.knst.telegram.ai.config;

public class AiConfig {
    public Server[] servers = new Server[] {
            new Server()
    };

    public Preset defaultUserPreset = new Preset();
    public Model defaultModel = new Model();
    public FilenameGeneration filenameGeneration = new FilenameGeneration();
    public ImagineSettings imagineSettings = new ImagineSettings();
    public Cache cache = new Cache();

    public static class ImagineSettings {
        public int serverIndexToUse = 0;
        public String model = "dall-e-3";
    }

    public static class FilenameGeneration {
        public boolean useGPT = true;
        public int serverIndexToUse = 0;
        public Preset preset = new Preset(
                "Generate a concise and relevant file name based on provided text. Provide only the file name without any additional text",
                0.3f, 0.6f, 0.5f,0.2f, 15
        );
        public Model model = new Model(
                "GPT-4o mini",
                "gpt-4o-mini",
                new String[] {}
        );
    }

    public static class Server {
        public String name = "OpenAI";
        public String token = "XXX";
        public String customUrl = "";
        public String project = "";
        public String organization = "";
    }

    public static class Preset {
        public String prompt = "You are the best artificial intelligence that helps a person answer his questions";
        public float temperature = 0.45f;
        public float topP = 0.5f;
        public float frequencyPenalty = 0.5f;
        public float presencePenalty = 0.2f;
        public int maxTokens = 512;

        public Preset() {}

        public Preset(String prompt, float temperature, float topP, float frequencyPenalty, float presencePenalty, int maxTokens) {
            this.prompt = prompt;
            this.temperature = temperature;
            this.topP = topP;
            this.frequencyPenalty = frequencyPenalty;
            this.presencePenalty = presencePenalty;
            this.maxTokens = maxTokens;
        }
    }

    public static class Model {
        public String name = "GPT-4 Omni";
        public String model = "gpt-4o";
        public String[] includedTools = new String[]{
                "calculate"
        };

        public Model() {}

        public Model(String name, String model, String[] includedTools) {
            this.name = name;
            this.model = model;
            this.includedTools = includedTools;
        }
    }

    public static class Cache {
        public int maxContexts = 200;
        public int maxPresets = 500;
        public int maxMessages = 1000;
        public int maxModels = 50;
    }
}
