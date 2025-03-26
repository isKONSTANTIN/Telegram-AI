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
import su.knst.telegram.ai.handlers.ChatHandler;
import su.knst.telegram.ai.jooq.tables.records.AiModelsRecord;
import su.knst.telegram.ai.jooq.tables.records.AiPresetsRecord;
import su.knst.telegram.ai.jooq.tables.records.ChatsPreferencesRecord;
import su.knst.telegram.ai.managers.ChatPreferencesManager;
import su.knst.telegram.ai.utils.menu.AskMenu;
import su.knst.telegram.ai.utils.EmojiList;
import su.knst.telegram.ai.utils.menu.TypedAskMenu;
import su.knst.telegram.ai.workers.AiWorker;
import su.knst.telegram.ai.workers.lang.LangWorker;

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
        AiConfig.Preset defaultPreset = configWorker.ai.get().defaultUserPreset;
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
        LangWorker lang = ((ChatHandler) scene.getChatHandler()).getLang().get();

        ChatsPreferencesRecord preferences = chatPreferencesManager.getPreferences(scene.getChatHandler().getChatId()).orElseThrow();

        layout.removeAll();

        if (selectedPresetId != -1) {
            updateSelectedPreset(lang);

            return super.apply();
        }

        setMessage(MessageBuilder.text(lang.get("scenes.settings.presets.title", """
                        🎨 Preset Selection Menu 🔧
        
                        Here, you can choose which preset you'd like to edit or configure. Each preset allows you to tailor the AI's behavior to match your specific needs.
                        Select a preset to modify its parameters, such as prompt style, response temperature, and other advanced settings.
                        """
        )));

        List<AiPresetsRecord> records = aiWorker.getPresetsManager().getPresets(scene.getChatHandler().getChatId());

        for (AiPresetsRecord record : records) {
            boolean isDefault = preferences.getDefaultPreset().equals(record.getId());
            boolean modelIsEnabled = aiWorker.getModelsManager().getModel(record.getModel()).orElseThrow().getEnabled();

            String title = "#" + record.getTag();

            if (isDefault)
                title += " " + EmojiList.STAR;

            if (!modelIsEnabled)
                title += " " + EmojiList.WARNING;

            layout.addButton(new InlineKeyboardButton(title), (e) -> {
                selectedPresetId = record.getId();
                apply();
            });
        }

        layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.buttons.new", "New Preset")), 2, (e) -> {
            AskMenu promptAsker = new AskMenu(scene);
            AskMenu tagAsker = new AskMenu(scene);

            promptAsker.setText(lang.get("scenes.settings.presets.new.title","Adding New Preset"), lang.get("scenes.settings.presets.new.promptEnterDescription","Enter prompt for new preset"))
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

            tagAsker.setText(lang.get("scenes.settings.presets.new.title","Adding New Preset"), lang.get("scenes.settings.presets.new.tagEnterDescription","Enter tag for new preset"))
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

        layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.buttons.back", "Back")), 2, backListener);

        return super.apply();
    }

    protected void updateSelectedPreset(LangWorker lang) {
        ChatsPreferencesRecord preferences = chatPreferencesManager.getPreferences(scene.getChatHandler().getChatId()).orElseThrow();

        AiPresetsRecord record = aiWorker.getPresetsManager().getPresets(scene.getChatHandler().getChatId())
                .stream()
                .filter((p) -> p.getId().equals(selectedPresetId))
                .findFirst()
                .orElseThrow();

        long defaultPreset = preferences.getDefaultPreset();
        boolean isDefault = defaultPreset == record.getId();

        AiModelsRecord model = aiWorker.getModelsManager().getModel(record.getModel()).orElseThrow();

        MessageBuilder builder = MessageBuilder.create();
        AiConfig.Server server = configWorker.ai.get().servers[model.getServer()];

        builder.bold().append("#" + record.getTag()).bold();

        if (isDefault)
            builder.append(" " + EmojiList.STAR);

        String[] tools = model.getIncludedTools();
        StringBuilder toolsString = new StringBuilder();

        if (tools.length == 0) {
            toolsString.append(lang.get("scenes.settings.presets.view.toolsList.empty", "Empty"));
        }else {
            for (String tool : tools)
                toolsString.append(lang.get("scenes.settings.presets.view.toolsList." + tool, tool.replaceAll("_", " "))).append(", ");

            toolsString.delete(toolsString.length() - 2, toolsString.length());
        }

        builder.gap().gap();
        builder.code().append(lang.get("scenes.settings.presets.view.prompt", "prompt")).gap().line(record.getText()).code().gap();

        String enabled;

        if (model.getEnabled()) {
            enabled = lang.get("scenes.settings.presets.view.enabled.yes", "Yes");
        }else {
            enabled = lang.get("scenes.settings.presets.view.enabled.no", "No " + EmojiList.WARNING);
        }

        builder.line(lang.get("scenes.settings.presets.view.parameters", """
                Parameters:
                 - Model:
                      - Name: %s (%s, %s)
                      - Tools: %s
                      - Enabled: %s
                 - Temperature: %.2f
                 - Top P: %.2f
                 - Frequency Penalty: %.2f
                 - Presence Penalty: %.2f
                 - Max Tokens: %d
                """
        ).formatted(
                model.getName(),
                model.getModel(),
                server.name,
                toolsString,
                enabled,
                record.getTemperature(),
                record.getTopP(),
                record.getFrequencyPenalty(),
                record.getPresencePenalty(),
                record.getMaxTokens()
        ));

        setMessage(builder.build());

        List<AiModelsRecord> models = aiWorker.getModelsManager().getModels();

        if (!isDefault) {
            layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.buttons.setAsDefault", "Set as default")), 2, (e) -> {
               chatPreferencesManager.setDefaultPreset(scene.getChatHandler().getChatId(), record.getId());

               apply();
            });
        }

        layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.buttons.edit", "Edit Model")), 1, (e) -> {
            AskMenu askMenu = new AskMenu(scene);

            models.forEach((m) -> {
                if (m.getEnabled())
                    askMenu.addAnswer(m.getName(), String.valueOf(m.getId()));
            });

            askMenu.setText(lang.get("scenes.settings.presets.editing.title", "Editing Model of #%s").formatted(record.getTag()), "")
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.temperature", "Edit Temperature"),
                lang.get("scenes.settings.presets.editing.temperature.title", "Editing Temperature of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.temperature.description",
                        "The temperature parameter controls the degree of randomness in the responses. " +
                                "At a low value (closer to 0), the answers will be more deterministic and conservative. " +
                                "With a high value (closer to 1), the answers will become more diverse and creative."),
                record.getTemperature(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.topP", "Edit Top P"),
                lang.get("scenes.settings.presets.editing.topP.title", "Editing Top P of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.topP.description",
                        "The Top P (or nucleus sampling) parameter restricts the choice of words based on " +
                                "the probability of the next word. The model will select among words whose sum of " +
                                "probabilities is at least the specified value. High values allow you to take into " +
                                "account a variety of options when choosing words"),
                record.getTopP(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.frequencyPenalty", "Edit Frequency Penalty"),
                lang.get("scenes.settings.presets.editing.frequencyPenalty.title", "Editing Frequency Penalty of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.frequencyPenalty.description",
                        "This parameter imposes a penalty on frequently occurring words. High values force the model to avoid repetition"),
                record.getFrequencyPenalty(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.presencePenalty", "Edit Presence Penalty"),
                lang.get("scenes.settings.presets.editing.presencePenalty.title", "Editing Presence Penalty of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.presencePenalty.description",
                        "This parameter controls the frequency of occurrence of already used words in the response. High values reduce the likelihood of the same words being used again"),record.getPresencePenalty(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.maxTokens", "Edit Max Tokens"),
                lang.get("scenes.settings.presets.editing.maxTokens.title", "Editing Max Tokens of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.maxTokens.description",
                        "Limits the maximum number of tokens that can be generated in a response"),
                record.getMaxTokens(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.prompt", "Edit Prompt"),
                lang.get("scenes.settings.presets.editing.prompt.title", "Editing Prompt of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.prompt.description",
                        "This parameter allows you to change the behavior of the AI"),
                record.getText(),
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

        addEditingButton(lang, lang.get("scenes.settings.presets.editing.buttons.tag", "Edit Tag"),
                lang.get("scenes.settings.presets.editing.tag.title", "Editing Tag of #%s").formatted(record.getTag()),
                lang.get("scenes.settings.presets.editing.tag.description",
                        "This parameter defines the name of the tag that will be searched in the requests to determine the behavior"),
                null,
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

        if (!isDefault) {
            layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.editing.buttons.remove", "Remove " + EmojiList.CANCEL)), 2, (e) -> {
                AskMenu askMenu = new AskMenu(scene);

                askMenu.addAnswer(lang.get("scenes.settings.presets.editing.delete.sure", "I'm sure"), "sure");

                askMenu.setText(lang.get("scenes.settings.presets.editing.delete.question", "Remove #%s?").formatted(record.getTag()), "")
                        .setResultFunction((s) -> {
                            if (s == null || !s.equals("sure")) {
                                apply();

                                return true;
                            }

                            aiWorker.deletePreset(record.getId(), defaultPreset);

                            selectedPresetId = -1;
                            apply();

                            return true;
                        })
                        .apply();
            });
        }

        layout.addButton(new InlineKeyboardButton(lang.get("scenes.settings.presets.editing.buttons.back", "Back")), 2, (e) -> {
            selectedPresetId = -1;
            apply();
        });
    }

    protected <T> void addEditingButton(LangWorker lang, String buttonTitle, String title, String description, T oldValue, Function<String, T> mapper, Function<T, Boolean> newValueFunction) {
        layout.addButton(new InlineKeyboardButton(buttonTitle), 1, (e) -> {
            TypedAskMenu<T> menu = new TypedAskMenu<>(scene);

            String descriptionWithOld = description;

            if (oldValue != null)
                descriptionWithOld += "\n\n```" + lang.get("scenes.settings.presets.editing.oldValue", "Old:") + "\n" + oldValue + "```";

            menu.setText(title, descriptionWithOld);
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
