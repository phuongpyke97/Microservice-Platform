package com.platform.crbtcommunitylibrary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.platform.common.core.exception.BaseException;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.entity.Category;
import com.platform.crbtcommunitylibrary.entity.Ringtone;
import com.platform.crbtcommunitylibrary.repository.CategoryRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneRepository;
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

    @InjectMocks
    private RingtoneService ringtoneService;

    @Test
    void createCategory_shouldReturnResponse() {
        Category saved = new Category("Pop", "Pop music");
        when(categoryRepository.save(any(Category.class))).thenReturn(saved);

        CategoryResponse response = ringtoneService.createCategory(new CategoryRequest("Pop", "Pop music"));

        assertEquals("Pop", response.name());
        assertEquals("Pop music", response.description());
    }

    @Test
    void createRingtone_shouldThrowWhenCategoryNotFound() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(BaseException.class, () ->
            ringtoneService.createRingtone(
                new RingtoneRequest("Title", "Artist", "http://audio.url", null, 180, false, 99L)
            )
        );
    }

    @Test
    void createRingtone_shouldReturnResponse() {
        Category category = new Category("Pop", "Pop music");
        Ringtone saved = new Ringtone("Song", "Artist", "http://url", null, 180, false, category);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(ringtoneRepository.save(any(Ringtone.class))).thenReturn(saved);

        RingtoneResponse response = ringtoneService.createRingtone(
            new RingtoneRequest("Song", "Artist", "http://url", null, 180, false, 1L)
        );

        assertEquals("Song", response.title());
        assertEquals("Artist", response.artistName());
        assertNotNull(response.category());
    }

    @Test
    void getRandomRingtone_shouldReturnRingtoneByGenre() {
        Category category = new Category("Pop", "Pop music");
        Ringtone ringtone = new Ringtone("Song", "Artist", "http://url", null, 180, false, category);
        when(ringtoneRepository.findRandomByGenre("Pop")).thenReturn(Optional.of(ringtone));

        RingtoneResponse response = ringtoneService.getRandomRingtone("Pop");

        assertNotNull(response);
        assertEquals("Song", response.title());
    }

    @Test
    void getRandomRingtone_shouldFallbackToGlobalRandomIfGenreNotFound() {
        Category category = new Category("Rock", "Rock music");
        Ringtone ringtone = new Ringtone("Song", "Artist", "http://url", null, 180, false, category);
        when(ringtoneRepository.findRandomByGenre("Pop")).thenReturn(Optional.empty());
        when(ringtoneRepository.findRandom()).thenReturn(Optional.of(ringtone));

        RingtoneResponse response = ringtoneService.getRandomRingtone("Pop");

        assertNotNull(response);
        assertEquals("Rock", response.category().name());
    }
}
