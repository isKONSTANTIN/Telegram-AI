package su.knst.telegram.ai.scenes.admin;

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
import su.knst.telegram.ai.managers.AiModelsManager;
import su.knst.telegram.ai.utils.menu.AskMenu;
import su.knst.telegram.ai.utils.menu.TypedAskMenu;
import su.knst.telegram.ai.workers.AiTools;
import su.knst.telegram.ai.workers.AiWorker;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ModelsMenu extends MessageMenu<FlexListButtonsLayout> {
    protected AiModelsManager modelsManager;
    protected AiTools aiTools;
    protected ConfigWorker configWorker;
    protected EventListener<CallbackQueryEvent> backListener;

    public ModelsMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, AiWorker aiWorker, ConfigWorker configWorker) {
        super(scene, new FlexListButtonsLayout(2));

        this.modelsManager = aiWorker.getModelsManager();
        this.aiTools = aiWorker.getAiTools();
        this.configWorker = configWorker;
        this.backListener = backListener;
    }

    protected void selected(int modelId) {
        layout.removeAll();

        MessageBuilder builder = MessageBuilder.create();
        builder.bold().line("Model View").bold();

        AiModelsRecord model = modelsManager.getModel(modelId).orElseThrow();
        AiConfig.Server server = configWorker.ai.get().servers[model.getServer()];

        String[] tools = model.getIncludedTools();
        String toolsString = tools.length == 0 ? "Empty" : String.join(", ", tools).replaceAll("_", " ");

        builder.gap().line(model.getName());
        builder.line(" - Model: " + model.getModel())
                .line(" - Server: " + server.name + " (#" + (model.getServer() + 1) + ")")
                .line(" - Tools: " + toolsString)
                .line(" - Costs: ")
                .line("     - Completion: $" + model.getCompletionTokensCost() + " / 1M tokens")
                .line("     - Prompt: $" + model.getPromptTokensCost() + " / 1M tokens")
                .line(" - Enabled: " + (model.getEnabled() ? "Yes" : "No"));

        layout.addButton(new InlineKeyboardButton("Edit Model"), 2, (e) -> {
            AskMenu askMenu = new AskMenu(scene);
            askMenu.setText("Editing model's model", "Enter new model");

            askMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());

                    return true;
                }

                modelsManager.editModel(modelId, model.getServer(), model.getName(), r, model.getIncludedTools());
                selected(model.getId());

                return true;
            });

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Edit Name"), (e) -> {
            AskMenu askMenu = new AskMenu(scene);
            askMenu.setText("Editing model's name", "Enter new name");

            askMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());

                    return true;
                }

                modelsManager.editModel(modelId, model.getServer(), r, model.getModel(), model.getIncludedTools());
                selected(model.getId());

                return true;
            });

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Edit Server"), (e) -> {
            TypedAskMenu<Integer> askMenu = new TypedAskMenu<>(scene);
            askMenu.setText("Editing model's server", "Select new server");

            AiConfig.Server[] servers = configWorker.ai.get().servers;

            for (int i = 0; i < servers.length; i++) {
                AiConfig.Server s = servers[i];
                askMenu.addAnswer(s.name, i);
            }

            askMenu.setResultFunction((r) -> {
                if (r == null || r < 0 || r >= configWorker.ai.get().servers.length) {
                    selected(model.getId());

                    return true;
                }

                modelsManager.editModel(modelId, r.shortValue(), model.getName(), model.getModel(), model.getIncludedTools());
                selected(model.getId());

                return true;
            }, Integer::parseInt);

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Add Tool"), (e) -> {
            AskMenu askMenu = new AskMenu(scene);
            askMenu.setText("Add model's tool", "Select tool to add");

            List<String> addedTools = new ArrayList<>(Arrays.asList(tools));

            List<String> availableTools = aiTools.getToolsMap()
                    .keySet().stream()
                    .filter((t) -> !addedTools.contains(t))
                    .toList();

            for (String tool : availableTools)
                askMenu.addAnswer(tool, tool);

            askMenu.addAnswer(new InlineKeyboardButton("Add All"), (ev) -> {
                modelsManager.editModel(modelId, model.getServer(), model.getName(), model.getModel(), availableTools.toArray(new String[0]));
                selected(model.getId());
            });

            askMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());
                    return true;
                }
                addedTools.add(r);

                modelsManager.editModel(modelId, model.getServer(), model.getName(), model.getModel(), addedTools.toArray(new String[0]));
                selected(model.getId());

                return true;
            });

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Remove Tool"), (e) -> {
            AskMenu askMenu = new AskMenu(scene);
            askMenu.setText("Remove model's tool", "Select tool to remove");

            for (String t : tools)
                askMenu.addAnswer(t, t);

            askMenu.addAnswer(new InlineKeyboardButton("Remove All"), (ev) -> {
                modelsManager.editModel(modelId, model.getServer(), model.getName(), model.getModel(), new String[0]);
                selected(model.getId());
            });

            askMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());
                    return true;
                }

                modelsManager.editModel(modelId, model.getServer(), model.getName(), model.getModel(), Arrays.stream(tools).filter(tool -> !tool.equals(r)).toArray(String[]::new));
                selected(model.getId());

                return true;
            });

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Edit Costs"), (e) -> {
            var result = new Object() {
                BigDecimal completion;
                BigDecimal prompt;
            };

            AskMenu askCompletionMenu = new AskMenu(scene);
            askCompletionMenu.setText("Set completion cost", "Enter completion cost per 1M tokens");

            AskMenu askPromptMenu = new AskMenu(scene);
            askPromptMenu.setText("Set prompt cost", "Enter prompt cost per 1M tokens");

            askCompletionMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());
                    return true;
                }

                try {
                    result.completion = new BigDecimal(r);
                }catch (NumberFormatException ignored) {
                    return false;
                }

                askPromptMenu.apply();

                return true;
            });

            askPromptMenu.setResultFunction((r) -> {
                if (r == null) {
                    selected(model.getId());
                    return true;
                }

                try {
                    result.prompt = new BigDecimal(r);
                }catch (NumberFormatException ignored) {
                    return false;
                }

                modelsManager.editCosts(modelId, result.completion, result.prompt);
                selected(model.getId());

                return true;
            });

            askCompletionMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Switch"), (e) -> {
            modelsManager.switchModel(modelId);
            selected(model.getId());
        });

        layout.addButton(new InlineKeyboardButton("Back"), 2, (e) -> applyMain());

        setMessage(builder.build());

        super.apply();
    }

    protected void applyMain() {
        layout.removeAll();
        MessageBuilder builder = MessageBuilder.create();
        builder.bold().line("Models Menu").bold();

        List<AiModelsRecord> models = modelsManager.getModels();

        for (AiModelsRecord model : models) {
            layout.addButton(new InlineKeyboardButton(model.getName()), (e) -> {
                selected(model.getId());
            });
        }

        setMessage(builder.build());

        layout.addButton(new InlineKeyboardButton("Add Model"), (e) -> {
            var newModel = new Object() {
                short server;
                String name;
                String model;
            };

            TypedAskMenu<Integer> askServer = new TypedAskMenu<>(scene);
            AskMenu askName = new AskMenu(scene);
            AskMenu askModel = new AskMenu(scene);

            askServer.setText("New model's server", "Select server");
            AiConfig.Server[] servers = configWorker.ai.get().servers;

            for (int i = 0; i < servers.length; i++) {
                AiConfig.Server s = servers[i];
                askServer.addAnswer(s.name, i);
            }

            askServer.setResultFunction((r) -> {
                if (r == null || r < 0 || r >= configWorker.ai.get().servers.length) {
                    applyMain();

                    return true;
                }

                newModel.server = r.shortValue();
                askName.apply();

                return true;
            }, Integer::parseInt);


            askName.setText("New model's name", "Enter model name");
            askName.setResultFunction((name) -> {
                if (name == null) {
                    applyMain();

                    return true;
                }

                newModel.name = name;
                askModel.apply();

                return true;
            });


            askModel.setText("New model's id", "Enter model id (gpt-4o, gpt-4o-mini, etc)");
            askModel.setResultFunction((model) -> {
                if (model == null) {
                    applyMain();

                    return true;
                }

                newModel.model = model;

                Optional<AiModelsRecord> record = modelsManager.addModel(newModel.server, newModel.name, newModel.model);

                if (record.isEmpty()) {
                    applyMain();

                    return true;
                }

                selected(record.get().getId());

                return true;
            });

            askServer.apply();
        });

        layout.addButton(new InlineKeyboardButton("Back"), 2, backListener);

        super.apply();
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        applyMain();

        return super.apply();
    }
}
