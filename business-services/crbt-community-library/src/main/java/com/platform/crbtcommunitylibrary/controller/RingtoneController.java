package com.platform.crbtcommunitylibrary.controller;

import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneSearchRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneStatisticsResponse;
import com.platform.crbtcommunitylibrary.service.RingtoneService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.success(ringtoneService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        ringtoneService.deleteCategory(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/ringtones")
    public ApiResponse<RingtoneResponse> createRingtone(@Valid @RequestBody RingtoneRequest request) {
        return ApiResponse.success(ringtoneService.createRingtone(request));
    }

    @PutMapping("/ringtones/{id}")
    public ApiResponse<RingtoneResponse> updateRingtone(
            @PathVariable Long id,
            @Valid @RequestBody RingtoneRequest request) {
        return ApiResponse.success(ringtoneService.updateRingtone(id, request));
    }

    @PatchMapping("/ringtones/{id}/status")
    public ApiResponse<RingtoneResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        Boolean status = body != null ? body.get("status") : null;
        if (status == null) {
            status = true;
        }
        return ApiResponse.success(ringtoneService.updateRingtoneStatus(id, status));
    }

    @GetMapping("/ringtones/search")
    public ApiResponse<PageResponse<RingtoneResponse>> search(
            RingtoneSearchRequest searchRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(ringtoneService.searchRingtones(searchRequest, pageable));
    }

    @GetMapping("/ringtones/statistics")
    public ApiResponse<RingtoneStatisticsResponse> getStatistics() {
        return ApiResponse.success(ringtoneService.getStatistics());
    }

    @GetMapping("/ringtones/export")
    public ResponseEntity<byte[]> exportRingtones(RingtoneSearchRequest searchRequest) {
        byte[] csvData = ringtoneService.exportRingtones(searchRequest);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ringtones.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csvData);
    }

    @DeleteMapping("/ringtones/{id}")
    public ApiResponse<Void> deleteRingtone(@PathVariable Long id) {
        ringtoneService.deleteRingtone(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/ringtones/random")
    public ApiResponse<RingtoneResponse> getRandom(@RequestParam(required = false) String genre) {
        return ApiResponse.success(ringtoneService.getRandomRingtone(genre));
    }
}
