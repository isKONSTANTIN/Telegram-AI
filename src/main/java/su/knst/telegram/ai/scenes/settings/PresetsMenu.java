package su.knst.telegram.ai.scenes.settings;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.response.BaseResponse;
import su.knst.telegram.ai.config.AiConfig;
import su.knst.telegram.ai.config.ConfigWorker;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.utils.menu.AskMenu;
import su.knst.telegram.ai.utils.EmojiList;
import su.knst.telegram.ai.utils.menu.TypedAskMenu;
import su.knst.telegram.ai.workers.AiWorker;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class PresetsMenu extends MessageMenu<FlexListButtonsLayout> {
    protected long selectedPresetId = -1;

    protected AiWorker aiWorker;
    protected ConfigWorker configWorker;
    protected ChatPreferencesManager chatPreferencesManager;

    protected EventListener<CallbackQueryEvent> backListener;

    protected String newTag;

    public PresetsMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, AiWorker aiWorker, ChatPreferencesManager chatPreferencesManager, ConfigWorker configWorker) {
        super(scene, new FlexListButtonsLayout(2));

        this.aiWorker = aiWorker;
        this.configWorker = configWorker;

        this.backListener = backListener;
        this.chatPreferencesManager = chatPreferencesManager;
    }

    protected boolean tagExists(String tag) {
        List<AiPresetsRecord> presets = aiWorker.getPresetsManager().getPresets(scene.getChatHandler().getChatId());

        return presets.stream().anyMatch(p -> p.getTag().equals(tag));
    }

    protected Optional<AiPresetsRecord> addNewPreset(String tag, String text) {
        AiConfig.Preset defaultPreset = configWorker.ai.defaultUserPreset;
        AiModelsRecord modelsRecord = aiWorker.getModelsManager().getModels().get(0);

        return aiWorker.getPresetsManager().addPreset(
                scene.getChatHandler().getChatId(),
                modelsRecord.getId(),
                defaultPreset.temperature,
                defaultPreset.topP,
                defaultPreset.frequencyPenalty,
                defaultPreset.presencePenalty,
                defaultPreset.maxTokens,
                text,
                tag
        );
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        ChatsPreferencesRecord preferences = chatPreferencesManager.getPreferences(scene.getChatHandler().getChatId()).orElseThrow();

        layout.removeAll();

        if (selectedPresetId != -1) {
            updateSelectedPreset();

            return super.apply();
        }

        setMessage(MessageBuilder.text("Presets"));

        List<AiPresetsRecord> records = aiWorker.getPresetsManager().getPresets(scene.getChatHandler().getChatId());

        for (AiPresetsRecord record : records) {
            boolean isDefault = preferences.getDefaultPreset().equals(record.getId());

            layout.addButton(new InlineKeyboardButton("#" + record.getTag() + (isDefault ? " " + EmojiList.STAR : "")), (e) -> {
                selectedPresetId = record.getId();
                apply();
            });
        }

        layout.addButton(new InlineKeyboardButton("New Preset"), 2, (e) -> {
            AskMenu promptAsker = new AskMenu(scene);
            AskMenu tagAsker = new AskMenu(scene);

            promptAsker.setText("Adding New Preset", "Enter prompt for new preset")
                    .setResultFunction((s) -> {
                        if (s == null) {
                            apply();

                            return true;
                        }

                        Optional<AiPresetsRecord> preset = addNewPreset(newTag, s);

                        if (preset.isEmpty())
                            return false;

                        selectedPresetId = preset.get().getId();
                        apply();

                        return true;
                    });

            tagAsker.setText("Adding New Preset", "Enter tag for new preset")
                    .setResultFunction((s) -> {
                        if (s == null) {
                            apply();

                            return true;
                        }

                        newTag = s.trim().replaceAll("#", "").replaceAll(" ", "_");

                        if (tagExists(newTag))
                            return false;

                        promptAsker.apply();

                        return true;
                    })
                    .apply();
        });

        layout.addButton(new InlineKeyboardButton("Back"), 2, backListener);

        return super.apply();
    }

    protected void updateSelectedPreset() {
        ChatsPreferencesRecord preferences = chatPreferencesManager.getPreferences(scene.getChatHandler().getChatId()).orElseThrow();

        AiPresetsRecord record = aiWorker.getPresetsManager().getPresets(scene.getChatHandler().getChatId())
                .stream()
                .filter((p) -> p.getId().equals(selectedPresetId))
                .findFirst()
                .orElseThrow();

        boolean isDefault = preferences.getDefaultPreset().equals(record.getId());

        AiModelsRecord model = aiWorker.getModelsManager().getModel(record.getModel()).orElseThrow();

        MessageBuilder builder = MessageBuilder.create();
        AiConfig.Server server = configWorker.ai.servers[model.getServer()];

        builder.bold().append("#" + record.getTag()).bold();

        if (isDefault)
            builder.append(" " + EmojiList.STAR);

        String[] tools = model.getIncludedTools();
        String toolsString = tools.length == 0 ? "Empty" : String.join(", ", tools).replaceAll("_", " ");

        builder.gap().gap();
        builder.code().append("prompt").gap().line(record.getText()).code().gap();

        builder.line("Parameters:");
        builder.line(" - Model:");
        builder.line("      - Name: " + model.getName() + " (" + model.getModel() + ", " + server.name + ")");
        builder.line("      - Tools: " + toolsString);
        builder.line("      - Enabled: " + (model.getEnabled()? "Yes" : "No"));
        builder.line(" - Temperature: " + record.getTemperature());
        builder.line(" - Top P: " + record.getTopP());
        builder.line(" - Frequency Penalty: " + record.getFrequencyPenalty());
        builder.line(" - Presence Penalty: " + record.getPresencePenalty());
        builder.line(" - Max Tokens: " + record.getMaxTokens());

        setMessage(builder.build());

        List<AiModelsRecord> models = aiWorker.getModelsManager().getModels();

        if (!isDefault) {
            layout.addButton(new InlineKeyboardButton("Set as default"), 2, (e) -> {
               chatPreferencesManager.setDefaultPreset(scene.getChatHandler().getChatId(), record.getId());

               apply();
            });
        }

        layout.addButton(new InlineKeyboardButton("Edit Model"), 1, (e) -> {
            AskMenu askMenu = new AskMenu(scene);

            models.forEach((m) -> askMenu.addAnswer(m.getName(), String.valueOf(m.getId())));

            askMenu.setText("Editing Model of #" + record.getTag(), "")
                    .setResultFunction((s) -> {
                        if (s == null) {
                            apply();

                            return true;
                        }
                        Optional<AiModelsRecord> optionalModel = Optional.empty();

                        try {
                            optionalModel = models.stream().filter((m) -> m.getId().equals(Integer.parseInt(s))).findAny();
                        } catch (NumberFormatException ignored) {}

                        if (optionalModel.isEmpty())
                            return false;

                        aiWorker.getPresetsManager().editPreset(record.getId(),
                                optionalModel.get().getId(),
                                record.getTemperature(),
                                record.getTopP(),
                                record.getFrequencyPenalty(),
                                record.getPresencePenalty(),
                                record.getMaxTokens(),
                                record.getText(),
                                record.getTag()
                        );

                        apply();

                        return true;
                    })
                    .apply();
        });

        addEditingButton("Edit Temperature",
                "Editing Temperature of #" + record.getTag(),
                "The temperature parameter controls the degree of randomness in the responses. " +
                "At a low value (closer to 0), the answers will be more deterministic and conservative. " +
                "With a high value (closer to 1), the answers will become more diverse and creative.",
                Float::parseFloat,
                (t) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        t,
                        record.getTopP(),
                        record.getFrequencyPenalty(),
                        record.getPresencePenalty(),
                        record.getMaxTokens(),
                        record.getText(),
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Top P",
                "Editing Top P of #" + record.getTag(),
                "The Top P (or nucleus sampling) parameter restricts the choice of words based on " +
                        "the probability of the next word. The model will select among words whose sum of " +
                        "probabilities is at least the specified value. High values allow you to take into " +
                        "account a variety of options when choosing words",
                Float::parseFloat,
                (p) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        p,
                        record.getFrequencyPenalty(),
                        record.getPresencePenalty(),
                        record.getMaxTokens(),
                        record.getText(),
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Frequency Penalty",
                "Editing Frequency Penalty of #" + record.getTag(),
                "This parameter imposes a penalty on frequently occurring words. High values force the model to avoid repetition",
                Float::parseFloat,
                (f) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        record.getTopP(),
                        f,
                        record.getPresencePenalty(),
                        record.getMaxTokens(),
                        record.getText(),
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Presence Penalty",
                "Editing Presence Penalty of #" + record.getTag(),
                "This parameter controls the frequency of occurrence of already used words in the response. High values reduce the likelihood of the same words being used again",
                Float::parseFloat,
                (p) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        record.getTopP(),
                        record.getFrequencyPenalty(),
                        p,
                        record.getMaxTokens(),
                        record.getText(),
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Max Tokens",
                "Editing Max Tokens of #" + record.getTag(),
                "Limits the maximum number of tokens that can be generated in a response",
                Integer::parseInt,
                (t) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        record.getTopP(),
                        record.getFrequencyPenalty(),
                        record.getPresencePenalty(),
                        t,
                        record.getText(),
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Text",
                "Editing Text of #" + record.getTag(),
                "This parameter allows you to change the behavior of the AI",
                (s) -> {
                    if (s.isBlank())
                        throw new InvalidParameterException();

                    return s;
                },
                (t) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        record.getTopP(),
                        record.getFrequencyPenalty(),
                        record.getPresencePenalty(),
                        record.getMaxTokens(),
                        t,
                        record.getTag()
                ).isPresent()
        );

        addEditingButton("Edit Tag",
                "Editing Tag of #" + record.getTag(),
                "This parameter defines the name of the tag that will be searched in the requests to determine the behavior",
                (s) -> {
                    if (s.isBlank())
                        throw new InvalidParameterException();

                    return s.replaceAll(" ", "_");
                },
                (t) -> aiWorker.getPresetsManager().editPreset(record.getId(),
                        model.getId(),
                        record.getTemperature(),
                        record.getTopP(),
                        record.getFrequencyPenalty(),
                        record.getPresencePenalty(),
                        record.getMaxTokens(),
                        record.getText(),
                        t
                ).isPresent()
        );

        layout.addButton(new InlineKeyboardButton("Back"), 2, (e) -> {
            selectedPresetId = -1;
            apply();
        });
    }

    protected <T> void addEditingButton(String buttonTitle, String title, String description, Function<String, T> mapper, Function<T, Boolean> newValueFunction) {
        layout.addButton(new InlineKeyboardButton(buttonTitle), 1, (e) -> {
            TypedAskMenu<T> menu = new TypedAskMenu<>(scene);

            menu.setText(title, description);
            menu.setResultFunction((r) -> {
                if (r == null) {
                    apply();

                    return true;
                }

                boolean result = newValueFunction.apply(r);

                if (result)
                    apply();

                return result;
            }, mapper);
            menu.apply();
        });
    }
}
