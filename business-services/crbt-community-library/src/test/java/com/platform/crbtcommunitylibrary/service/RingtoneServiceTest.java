package com.platform.crbtcommunitylibrary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.common.core.exception.BaseException;
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

    @Test
    void createRingtone_shouldThrowWhenFileSizeExceedsLimit() {
        Category category = new Category("Pop", "Pop music");
        Mood mood = new Mood("Vui", "");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(moodRepository.findById(2L)).thenReturn(Optional.of(mood));

        long largeSize = 51L * 1024 * 1024;
        when(audioDurationParser.analyzeAudio("http://url"))
            .thenReturn(new com.platform.crbtcommunitylibrary.util.AudioAnalysisResult(180, largeSize, false));

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Song", "Artist", "http://url", null, 180, false, 2L, true, 1L)
            )
        );
    }

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
}
