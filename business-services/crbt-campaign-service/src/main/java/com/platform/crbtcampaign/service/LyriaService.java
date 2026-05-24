package com.platform.crbtcampaign.service;

import com.platform.common.ai.LyriaSystemPromptConfig;
import com.platform.crbtcampaign.client.LibraryClient;
import com.platform.crbtcampaign.client.LyriaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LyriaService {
    private static final Logger log = LoggerFactory.getLogger(LyriaService.class);

    private final LyriaClient lyriaClient;
    private final LibraryClient libraryClient;
    private final LyriaSystemPromptConfig promptConfig;

    public LyriaService(LyriaClient lyriaClient,
                        LibraryClient libraryClient,
                        LyriaSystemPromptConfig promptConfig) {
        this.lyriaClient = lyriaClient;
        this.libraryClient = libraryClient;
        this.promptConfig = promptConfig;
    }

    public byte[] generateMusic(String genre, String mood, String instrument) {
        String prompt = promptConfig.buildPrompt(genre, mood, instrument);
        try {
            log.info("Calling Lyria 3 with prompt: {}", prompt);
            return lyriaClient.generateMusic(prompt);
        } catch (Exception e) {
            log.warn("Lyria 3 failed, falling back to library. Error: {}", e.getMessage());
            // In real impl, we would extract bytes from LibraryClient response
            // For now, return empty or throw if fallback fails
            try {
                Object resp = libraryClient.getRandomRingtone(genre).data();
                log.info("Fallback success: retrieved random ringtone for genre {}", genre);
                return new byte[0]; // Placeholder for actual bytes
            } catch (Exception ex) {
                log.error("Fallback also failed", ex);
                throw new RuntimeException("Music generation failed");
            }
        }
    }
}
