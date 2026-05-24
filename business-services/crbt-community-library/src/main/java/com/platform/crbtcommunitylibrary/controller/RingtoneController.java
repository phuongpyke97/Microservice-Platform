package com.platform.crbtcommunitylibrary.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.service.RingtoneService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/library")
public class RingtoneController {

    private final RingtoneService ringtoneService;

    public RingtoneController(RingtoneService ringtoneService) {
        this.ringtoneService = ringtoneService;
    }

    @PostMapping("/categories")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(ringtoneService.createCategory(request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(ringtoneService.getAllCategories());
    }

    @PostMapping("/ringtones")
    public ApiResponse<RingtoneResponse> createRingtone(@Valid @RequestBody RingtoneRequest request) {
        return ApiResponse.success(ringtoneService.createRingtone(request));
    }

    @GetMapping("/ringtones/search")
    public ApiResponse<PageResponse<RingtoneResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(ringtoneService.searchRingtones(q, categoryId, featured, pageable));
    }

    @GetMapping("/ringtones/random")
    public ApiResponse<RingtoneResponse> getRandom(@RequestParam(required = false) String genre) {
        return ApiResponse.success(ringtoneService.getRandomRingtone(genre));
    }
}
