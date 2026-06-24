package com.platform.crbtcommunitylibrary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.common.core.exception.BaseException;
import com.platform.crbtcommunitylibrary.client.CampaignClient;
import com.platform.crbtcommunitylibrary.client.FileServiceClient;
import com.platform.crbtcommunitylibrary.dto.request.ApproveAiToneRequest;
import com.platform.crbtcommunitylibrary.dto.request.ApproveDiyToneRequest;
import com.platform.common.core.response.ApiResponse;
import java.util.List;
import java.util.Map;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.MoodRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.MoodResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneStatisticsResponse;
import com.platform.crbtcommunitylibrary.entity.Category;
import com.platform.crbtcommunitylibrary.entity.Mood;
import com.platform.crbtcommunitylibrary.entity.Ringtone;
import com.platform.crbtcommunitylibrary.repository.CategoryRepository;
import com.platform.crbtcommunitylibrary.repository.MoodRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneDeletedHistoryRepository;
import com.platform.crbtcommunitylibrary.util.AudioDurationParser;
import io.minio.MinioClient;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RingtoneServiceTest {

    @Mock
    private RingtoneRepository ringtoneRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MoodRepository moodRepository;

    @Mock
    private RingtoneDeletedHistoryRepository ringtoneDeletedHistoryRepository;

    @Mock
    private AudioDurationParser audioDurationParser;

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private RingtoneService ringtoneService;

    @Mock
    private CampaignClient campaignClient;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private com.platform.crbtcommunitylibrary.client.AuthServiceClient authServiceClient;

    // ─── Category tests ────────────────────────────────────────────────────────

    @Test
    void createCategory_shouldReturnResponse() {
        Category saved = new Category("Pop", "Pop music");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = ringtoneService.createCategory(new CategoryRequest("Pop", "Pop music"));

        assertEquals("Pop", response.name());
        assertEquals("Pop music", response.description());
    }

    // ─── Mood tests ────────────────────────────────────────────────────────────

    @Test
    void createMood_shouldReturnResponse() {
        Mood saved = new Mood("Vui", "Vui vẻ, tích cực");
        when(moodRepository.save(any(Mood.class))).thenReturn(saved);

        MoodResponse response = ringtoneService.createMood(new MoodRequest("Vui", "Vui vẻ, tích cực"));

        assertEquals("Vui", response.name());
    }

    @Test
    void deleteMood_shouldThrowWhenUsedByRingtones() {
        Mood mood = new Mood("Vui", "");
        when(moodRepository.findById(1L)).thenReturn(Optional.of(mood));
        when(ringtoneRepository.countByMoodIdAndDeletedFalse(1L)).thenReturn(3L);

        assertThrows(BaseException.class, () -> ringtoneService.deleteMood(1L));
    }

    @Test
    void deleteCategory_shouldThrowWhenUsedByRingtones() {
        Category category = new Category("Pop", "Pop music");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(ringtoneRepository.countByCategoryIdAndDeletedFalse(1L)).thenReturn(2L);

        assertThrows(BaseException.class, () -> ringtoneService.deleteCategory(1L));
    }

    // ─── Ringtone tests ────────────────────────────────────────────────────────

    @Test
    void createRingtone_shouldThrowWhenCategoryNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Title", "Artist", "http://audio.url", null, 180, false, 1L, true, 99L)
            )
        );
    }

    @Test
    void createRingtone_shouldThrowWhenMoodNotFound() {
        Category category = new Category("Pop", "Pop music");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Title", "Artist", "http://audio.url", null, 180, false, 99L, true, 1L)
            )
        );
    }

    @Test
    void createRingtone_shouldReturnResponse() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        Ringtone saved = new Ringtone("Song", "Artist", "http://url", null, 180, false, mood, true, category);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(2L)).thenReturn(Optional.of(mood));
        when(audioDurationParser.analyzeAudio("http://url")).thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(180, 1000L, false));
        when(ringtoneRepository.save(any(Ringtone.class))).thenReturn(saved);

        RingtoneResponse response = ringtoneService.createRingtone(
            new RingtoneRequest("Song", "Artist", "http://url", null, 180, false, 2L, true, 1L)
        );

        assertEquals("Song", response.title());
        assertEquals("Artist", response.artistName());
        assertNotNull(response.category());
        assertNotNull(response.mood());
        assertEquals("Vui", response.mood().name());
    }

    @Test
    void getRandomRingtone_shouldReturnRingtoneByGenre() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        Ringtone ringtone = new Ringtone("Song", "Artist", "http://url", null, 180, false, mood, true, category);
        when(ringtoneRepository.findRandomByGenre("Pop")).thenReturn(Optional.of(ringtone));

        RingtoneResponse response = ringtoneService.getRandomRingtone("Pop");

        assertNotNull(response);
        assertEquals("Song", response.title());
    }

    @Test
    void getRandomRingtone_shouldFallbackToGlobalRandomIfGenreNotFound() {
        Category category = new Category("Rock", "Rock music");
        Mood mood = new Mood("Hype", "");
        Ringtone ringtone = new Ringtone("Song", "Artist", "http://url", null, 180, false, mood, true, category);
        when(ringtoneRepository.findRandomByGenre("Pop")).thenReturn(Optional.empty());
        when(ringtoneRepository.findRandom()).thenReturn(Optional.of(ringtone));

        RingtoneResponse response = ringtoneService.getRandomRingtone("Pop");

        assertNotNull(response);
        assertEquals("Rock", response.category().name());
    }

    @Test
    void getStatistics_shouldAggregateActiveAndArchivedCounts() {
        when(ringtoneRepository.countByDeletedFalse()).thenReturn(10L);
        when(ringtoneRepository.countByDeletedFalseAndStatusTrue()).thenReturn(8L);
        when(ringtoneRepository.countByDeletedFalseAndStatusFalse()).thenReturn(2L);
        when(ringtoneRepository.sumSelectionCountByDeletedFalse()).thenReturn(100L);
        when(ringtoneDeletedHistoryRepository.sumSelectionCount()).thenReturn(50L);

        RingtoneStatisticsResponse stats = ringtoneService.getStatistics();

        assertEquals(10L, stats.totalTracks());
        assertEquals(8L, stats.activeTracks());
        assertEquals(2L, stats.inactiveTracks());
        assertEquals(150L, stats.totalSelections()); // 100 active + 50 archived
    }

//    @Test
//    void createRingtone_shouldThrowWhenFileSizeExceedsLimit() {
//        Category category = new Category("Pop", "Pop music");
//        Mood mood = new Mood("Vui", "");
//        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
//        when(moodRepository.findById(2L)).thenReturn(Optional.of(mood));
//
//        long largeSize = 51L * 1024 * 1024;
//        when(audioDurationParser.analyzeAudio("http://url"))
//            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(180, largeSize, false));
//
//        assertThrows(BaseException.class, () ->
//            ringtoneService.createRingtone(
//                new RingtoneRequest("Song", "Artist", "http://url", null, 180, false, 2L, true, 1L)
//            )
//        );
//    }

    @Test
    void createRingtone_shouldThrowWhenDurationExceedsLimit() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(2L)).thenReturn(Optional.of(mood));

        when(audioDurationParser.analyzeAudio("http://url"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(300, 1000L, false));

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Song", "Artist", "http://url", null, 300, false, 2L, true, 1L)
            )
        );
    }

    @Test
    void createRingtone_shouldThrowWhenVocalDetected() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(2L)).thenReturn(Optional.of(mood));

        when(audioDurationParser.analyzeAudio("http://url"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(180, 1000L, true));

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Song", "Artist", "http://url", null, 180, false, 2L, true, 1L)
            )
        );
    }

    @Test
    void getFallbackRingtone_shouldReturnRingtone() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        Ringtone ringtone = new Ringtone("Guitar Solo", "Artist", "http://url", null, 30, false, mood, true, category);
        
        when(ringtoneRepository.findRandomByGenreAndMoodAndInstrument("pop", "vui", "guitar"))
            .thenReturn(Optional.of(ringtone));

        RingtoneResponse response = ringtoneService.getFallbackRingtone("pop", "vui", "guitar");

        assertNotNull(response);
        assertEquals("Guitar Solo", response.title());
    }

    @Test
    void approveAiTone_shouldMapSuccessfullyAndSave() {
        CampaignClient.UserLyriaHistoryResponse history = new CampaignClient.UserLyriaHistoryResponse(
            10L, 3L, "096868686", "AI Ringtone", "Pop", "Vui", "guitar", "http://audio-url.mp3", 30
        );
        when(campaignClient.getLyriaHistory(10L)).thenReturn(ApiResponse.success(history));

        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findByNameIgnoreCase("Pop")).thenReturn(Optional.of(category));
        when(moodRepository.findByNameIgnoreCase("Vui")).thenReturn(Optional.of(mood));

        when(audioDurationParser.analyzeAudio("http://audio-url.mp3"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(30, 1000L, false));

        Ringtone savedRingtone = new Ringtone("AI Ringtone", "AI Composer", "http://audio-url.mp3", null, 30, false, mood, true, category);
        savedRingtone.setAiGenerated(true);
        when(ringtoneRepository.save(any(Ringtone.class))).thenReturn(savedRingtone);

        ApproveAiToneRequest request = new ApproveAiToneRequest("AI_10", null, null, null, null, null, null, null);
        RingtoneResponse response = ringtoneService.approveAiTone(request);

        assertNotNull(response);
        assertEquals("AI Ringtone", response.title());
        assertEquals("AI Composer", response.artistName());
        assertEquals(true, response.isAiGenerated());
    }

    @Test
    void approveAiTone_shouldRandomizeCategoryAndMoodWhenNotFound() {
        CampaignClient.UserLyriaHistoryResponse history = new CampaignClient.UserLyriaHistoryResponse(
            10L, 3L, "096868686", "AI Ringtone", "NonExistentGenre", "NonExistentMood", "guitar", "http://audio-url.mp3", 30
        );
        when(campaignClient.getLyriaHistory(10L)).thenReturn(ApiResponse.success(history));

        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findByNameIgnoreCase("NonExistentGenre")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        
        when(moodRepository.findByNameIgnoreCase("NonExistentMood")).thenReturn(Optional.empty());
        when(moodRepository.findAll()).thenReturn(List.of(mood));

        when(audioDurationParser.analyzeAudio("http://audio-url.mp3"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(30, 1000L, false));

        Ringtone savedRingtone = new Ringtone("AI Ringtone", "AI Composer", "http://audio-url.mp3", null, 30, false, mood, true, category);
        savedRingtone.setAiGenerated(true);
        when(ringtoneRepository.save(any(Ringtone.class))).thenReturn(savedRingtone);

        ApproveAiToneRequest request = new ApproveAiToneRequest("AI_10", null, null, null, null, null, null, null);
        RingtoneResponse response = ringtoneService.approveAiTone(request);

        assertNotNull(response);
        assertEquals("AI Ringtone", response.title());
        assertEquals(true, response.isAiGenerated());
    }

    @Test
    void approveAiTone_shouldThrowExceptionWhenInvalidIdFormat() {
        ApproveAiToneRequest requestDiy = new ApproveAiToneRequest("DIY_10", null, null, null, null, null, null, null);
        org.junit.jupiter.api.Assertions.assertThrows(
            com.platform.common.core.exception.BaseException.class,
            () -> ringtoneService.approveAiTone(requestDiy)
        );

        ApproveAiToneRequest requestNoPrefix = new ApproveAiToneRequest("10", null, null, null, null, null, null, null);
        org.junit.jupiter.api.Assertions.assertThrows(
            com.platform.common.core.exception.BaseException.class,
            () -> ringtoneService.approveAiTone(requestNoPrefix)
        );
    }

    @Test
    void createRingtone_shouldThrowExceptionWhenAudioUrlAlreadyExists() {
        RingtoneRequest request = new RingtoneRequest("Title", "Artist", "http://existing-audio.mp3", null, 30, false, 1L, true, 1L);
        Category category = new Category("Pop", "");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(1L)).thenReturn(Optional.of(mood));
        
        Ringtone existingRingtone = new Ringtone("Title", "Artist", "http://existing-audio.mp3", null, 30, false, mood, true, category);
        when(ringtoneRepository.findByAudioUrlAndDeletedFalse("http://existing-audio.mp3")).thenReturn(Optional.of(existingRingtone));

        assertThrows(BaseException.class, () -> ringtoneService.createRingtone(request));
    }

    @Test
    void approveAiTone_shouldThrowExceptionWhenAudioUrlAlreadyExists() {
        CampaignClient.UserLyriaHistoryResponse history = new CampaignClient.UserLyriaHistoryResponse(
            10L, 1L, "0987654321", "Title", "Pop", "Vui", "", "http://existing-audio.mp3", 30
        );
        when(campaignClient.getLyriaHistory(10L)).thenReturn(ApiResponse.success(history));

        Category category = new Category("Pop", "");
        Mood mood = new Mood("Vui", "");
        Ringtone existingRingtone = new Ringtone("Title", "Artist", "http://existing-audio.mp3", null, 30, false, mood, true, category);
        when(ringtoneRepository.findByAudioUrlAndDeletedFalse("http://existing-audio.mp3")).thenReturn(Optional.of(existingRingtone));

        ApproveAiToneRequest request = new ApproveAiToneRequest("AI_10", null, null, null, null, null, null, null);
        assertThrows(BaseException.class, () -> ringtoneService.approveAiTone(request));
    }

    @Test
    void approveDiyTone_shouldMapSuccessfullyAndSave() {
        FileServiceClient.FileMetadataResponse metadata = new FileServiceClient.FileMetadataResponse(
            123L, 3L, "user-bg.mp3", "123-user-bg.mp3", "media-audio-lib", "audio/mpeg", 2000000L, "CONFIRMED"
        );
        when(fileServiceClient.getFileMetadata(123L)).thenReturn(ApiResponse.success(metadata));
        when(authServiceClient.getMsisdnsByUserIds(List.of(3L))).thenReturn(Map.of(3L, "84987654321"));
        when(fileServiceClient.copyToPublic(123L, "media-audio")).thenReturn(ApiResponse.success("http://external/media-audio/123-user-bg.mp3"));

        Category category = new Category("EDM", "EDM music");
        Mood mood = new Mood("Calm", "");
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(moodRepository.findAll()).thenReturn(List.of(mood));

        when(audioDurationParser.analyzeAudio("http://external/media-audio/123-user-bg.mp3"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(45, 2000000L, false));

        Ringtone savedRingtone = new Ringtone("DIY Tone", "DIY Composer", "http://external/media-audio/123-user-bg.mp3", null, 45, false, mood, true, category);
        savedRingtone.setAiGenerated(false);
        when(ringtoneRepository.save(any(Ringtone.class))).thenReturn(savedRingtone);

        ApproveDiyToneRequest request = new ApproveDiyToneRequest(123L, null, null, null, null, null, null, null);
        RingtoneResponse response = ringtoneService.approveDiyTone(request);

        assertNotNull(response);
        assertEquals("DIY Tone", response.title());
        assertEquals("DIY Composer", response.artistName());
        assertEquals(false, response.isAiGenerated());
    }

    @Test
    void approveDiyTone_shouldThrowExceptionWhenFileNotConfirmed() {
        FileServiceClient.FileMetadataResponse metadata = new FileServiceClient.FileMetadataResponse(
            123L, 3L, "user-bg.mp3", "123-user-bg.mp3", "media-temp", "audio/mpeg", 2000000L, "UPLOADED"
        );
        when(fileServiceClient.getFileMetadata(123L)).thenReturn(ApiResponse.success(metadata));

        ApproveDiyToneRequest request = new ApproveDiyToneRequest(123L, null, null, null, null, null, null, null);
        assertThrows(BaseException.class, () -> ringtoneService.approveDiyTone(request));
    }
}
