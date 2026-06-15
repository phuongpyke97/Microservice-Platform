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
    public static final Voice MY_NILAR = new Voice("my-MM-NilarNeural", "my-MM", "Female");
    public static final Voice EN_GUY = new Voice("en-US-GuyNeural", "en-US", "Male");
    public static final Voice EN_JENNY = new Voice("en-US-JennyNeural", "en-US", "Female");

    public static final List<Voice> SUPPORTED_VOICES =
        List.of(VI_HOAI_MY, VI_NAM_MINH, MY_THIHA, MY_NILAR, EN_GUY, EN_JENNY);

    private EdgeTtsVoiceMetadata() {
    }

    public record Voice(String id, String locale, String gender) {
    }
}
