package su.knst.telegram.ai.scenes.main;

import app.finwave.tat.event.chat.NewMessageEvent;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.PhotoSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AlbumData {
    protected String mediaGroupId;
    protected String caption;
    protected ArrayList<PhotoSize[]> photos = new ArrayList<>();
    protected ArrayList<NewMessageEvent> events = new ArrayList<>();
    protected long lastAddingTime = -1;

    public boolean add(NewMessageEvent event) {
        Message message = event.data;

        if (message == null || message.mediaGroupId() == null || message.photo() == null)
            return false;

        if (mediaGroupId == null)
            mediaGroupId = event.data.mediaGroupId();

        if (!mediaGroupId.equals(message.mediaGroupId()))
            return false;

        if (caption == null)
            caption = message.caption();

        photos.add(message.photo());
        events.add(event);

        lastAddingTime = System.currentTimeMillis();

        return true;
    }

    public long getLastAddingTime() {
        return lastAddingTime;
    }

    public String getMediaGroupId() {
        return mediaGroupId;
    }

    public String getCaption() {
        return caption;
    }

    public List<PhotoSize[]> getPhotos() {
        return Collections.unmodifiableList(photos);
    }

    public List<NewMessageEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    @Override
    public String toString() {
        return "AlbumData{" +
                "mediaGroupId='" + mediaGroupId + '\'' +
                ", caption='" + caption + '\'' +
                ", photos=" + photos +
                ", events=" + events +
                ", lastAddingTime=" + lastAddingTime +
                '}';
    }
}
