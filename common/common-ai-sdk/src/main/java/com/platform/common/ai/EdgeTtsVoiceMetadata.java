package com.platform.common.ai;

import java.util.List;

/**
 * Edge TTS voices used by the ai-media-worker.
 * Names match Microsoft Edge TTS voice IDs.
 */
public final class EdgeTtsVoiceMetadata {

    public static final Voice VI_HOAI_MY = new Voice("vi-VN-HoaiMyNeural", "vi-VN", "Female");
    public static final Voice VI_NAM_MINH = new Voice("vi-VN-NamMinhNeural", "vi-VN", "Male");
    public static final Voice MY_THIHA = new Voice("my-MM-ThihaNeural", "my-MM", "Male");

    public static final List<Voice> SUPPORTED_VOICES = List.of(VI_HOAI_MY, VI_NAM_MINH, MY_THIHA);

    private EdgeTtsVoiceMetadata() {
    }

    public record Voice(String id, String locale, String gender) {
    }
}
