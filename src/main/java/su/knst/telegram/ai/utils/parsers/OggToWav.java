package su.knst.telegram.ai.utils.parsers;

import ws.schild.jave.Encoder;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.File;
import java.io.IOException;

public class OggToWav {

    public static File ogg2Wav(File oggFile) throws IOException, EncoderException {
        File tempFile = File.createTempFile("knst_ai_ogg_to_wav_", ".wav");

        AudioAttributes audio = new AudioAttributes();
        audio.setCodec("pcm_s16le");

        EncodingAttributes attrs = new EncodingAttributes();
        attrs.setOutputFormat("wav");
        attrs.setAudioAttributes(audio);

        Encoder encoder = new Encoder();
        encoder.encode(new MultimediaObject(oggFile), tempFile, attrs);

        return tempFile;
    }

}
