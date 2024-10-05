package su.knst.telegram.ai.scenes.admin;

import app.finwave.tat.event.chat.CallbackQueryEvent;
import app.finwave.tat.event.chat.NewMessageEvent;
import app.finwave.tat.event.handler.EventListener;
import app.finwave.tat.event.handler.HandlerRemover;
import app.finwave.tat.menu.FlexListButtonsLayout;
import app.finwave.tat.menu.MessageMenu;
import app.finwave.tat.scene.BaseScene;
import app.finwave.tat.utils.MessageBuilder;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.message.origin.MessageOrigin;
import com.pengrad.telegrambot.model.message.origin.MessageOriginUser;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.response.BaseResponse;
import su.knst.telegram.ai.jooq.tables.records.ChatsWhitelistRecord;
import su.knst.telegram.ai.managers.WhitelistManager;
import su.knst.telegram.ai.utils.menu.AskMenu;
import su.knst.telegram.ai.utils.menu.TypedAskMenu;
import su.knst.telegram.ai.workers.BotWorker;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WhitelistMenu extends MessageMenu<FlexListButtonsLayout> {
    protected WhitelistManager whitelistManager;
    protected User me;
    protected HandlerRemover remover;

    public WhitelistMenu(BaseScene<?> scene, EventListener<CallbackQueryEvent> backListener, WhitelistManager whitelistManager) {
        super(scene, new FlexListButtonsLayout(1));

        this.whitelistManager = whitelistManager;
        this.me = scene.getChatHandler().getCore().getMe().orElseThrow();

        layout.addButton(new InlineKeyboardButton("Add"), (e) -> {
            TypedAskMenu<Long> askMenu = new TypedAskMenu<>(scene);
            askMenu.setText("Enter user or chat id", "");

            askMenu.setResultFunction((id) -> {
                if (id == null) {
                    apply();

                    return true;
                }

                whitelistManager.addToWhitelist(id);
                apply();

                return true;
            }, Long::parseLong);

            askMenu.apply();
        });

        layout.addButton(new InlineKeyboardButton("Back"), backListener);
    }

    protected void messageHandler(NewMessageEvent event) {
        scene.getChatHandler().deleteMessage(event.data.messageId());

        if (event.data.text() == null)
            return;

        String[] words = event.data.text().split(" ");

        if (words.length != 2)
            return;

        long id;

        try {
            id = Long.parseLong(words[1]);
        }catch (NumberFormatException ignored) {
            return;
        }

        switch (words[0]) {
            case "Switch" -> {
                whitelistManager.switchFromWhitelist(id);
                remover.remove();
                apply();
            }
            case "Edit" -> {
                AskMenu askMenu = new AskMenu(scene);

                askMenu.setText("Editing description of " + id, "Enter new description");
                askMenu.setResultFunction(r -> {
                    if (r == null || r.isBlank()) {
                        apply();

                        return true;
                    }

                    whitelistManager.editDescription(id, r);
                    apply();

                    return true;
                });

                remover.remove();
                askMenu.apply();
            }
        }
    }

    @Override
    public CompletableFuture<? extends BaseResponse> apply() {
        List<ChatsWhitelistRecord> records = whitelistManager.getWhitelist();
        String myUsername = me.username();

        MessageBuilder builder = MessageBuilder.create().bold().line("Chats and users whitelist").bold();

        for (ChatsWhitelistRecord record : records) {
            builder.gap()
                    .append(String.valueOf(record.getId()))
                    .append(" - ")
                    .append(record.getDescription())
                    .append("; ");

            String access = record.getEnabled() ? "Allowed" : "Denied";
            builder.url("tg://resolve?domain=" + myUsername + "&text=Switch " + record.getId(), access);

            builder.append("; ");
            builder.url("tg://resolve?domain=" + myUsername + "&text=Edit " + record.getId(), "Edit description");
        }

        if (records.isEmpty())
            builder.gap().line("List is empty");

        setMessage(builder.build());

        if (remover != null)
            remover.remove();

        remover = scene.getEventHandler().registerListener(NewMessageEvent.class, this::messageHandler);

        return super.apply();
    }
}
