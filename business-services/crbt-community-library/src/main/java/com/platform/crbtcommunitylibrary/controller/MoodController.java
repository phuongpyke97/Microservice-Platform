package com.platform.crbtcommunitylibrary.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.crbtcommunitylibrary.dto.request.MoodRequest;
import com.platform.crbtcommunitylibrary.dto.response.MoodResponse;
import com.platform.crbtcommunitylibrary.service.RingtoneService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/library/moods")
public class MoodController {

    private final RingtoneService ringtoneService;

    public MoodController(RingtoneService ringtoneService) {
        this.ringtoneService = ringtoneService;
    }

    /**
     * Create a new Mood library item.
     */
    @PostMapping
    public ApiResponse<MoodResponse> createMood(@Valid @RequestBody MoodRequest request) {
        return ApiResponse.success(ringtoneService.createMood(request));
    }

    /**
     * List all available moods.
     */
    @GetMapping
    public ApiResponse<List<MoodResponse>> getAllMoods() {
        return ApiResponse.success(ringtoneService.getAllMoods());
    }

    /**
     * Update an existing Mood.
     */
    @PutMapping("/{id}")
    public ApiResponse<MoodResponse> updateMood(
            @PathVariable Long id,
            @Valid @RequestBody MoodRequest request) {
        return ApiResponse.success(ringtoneService.updateMood(id, request));
    }

    /**
     * Delete a Mood. Fails if any active ringtone is using it.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteMood(@PathVariable Long id) {
        ringtoneService.deleteMood(id);
        return ApiResponse.success(null);
    }
}
