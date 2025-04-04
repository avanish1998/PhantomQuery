package com.aiinterview.service;

import org.springframework.stereotype.Service;
import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class AudioCaptureService {
    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = true;

    private volatile boolean isRecording = false;
    private TargetDataLine line;
    private ByteArrayOutputStream audioData;

    public void startCapture() throws LineUnavailableException {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        
        if (!AudioSystem.isLineSupported(info)) {
            throw new LineUnavailableException("Line not supported");
        }

        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();
        
        isRecording = true;
        audioData = new ByteArrayOutputStream();
        
        Thread captureThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (isRecording) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    audioData.write(buffer, 0, count);
                }
            }
        });
        captureThread.start();
    }

    public byte[] stopCapture() {
        isRecording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        return audioData.toByteArray();
    }

    public boolean isRecording() {
        return isRecording;
    }
} 