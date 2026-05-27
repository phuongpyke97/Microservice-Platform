package com.platform.crbtcommunitylibrary.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneSearchRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneStatisticsResponse;
import com.platform.crbtcommunitylibrary.entity.Category;
import com.platform.crbtcommunitylibrary.entity.Ringtone;
import com.platform.crbtcommunitylibrary.entity.RingtoneDeletedHistory;
import com.platform.crbtcommunitylibrary.repository.CategoryRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneDeletedHistoryRepository;
import com.platform.crbtcommunitylibrary.util.AudioAnalysisResult;
import com.platform.crbtcommunitylibrary.util.AudioDurationParser;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.persistence.criteria.Predicate;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RingtoneService {

    private final RingtoneRepository ringtoneRepository;
    private final CategoryRepository categoryRepository;
    private final RingtoneDeletedHistoryRepository ringtoneDeletedHistoryRepository;
    private final AudioDurationParser audioDurationParser;
    private final MinioClient minioClient;

    public RingtoneService(
        RingtoneRepository ringtoneRepository,
        CategoryRepository categoryRepository,
        RingtoneDeletedHistoryRepository ringtoneDeletedHistoryRepository,
        AudioDurationParser audioDurationParser,
        MinioClient minioClient
    ) {
        this.ringtoneRepository = ringtoneRepository;
        this.categoryRepository = categoryRepository;
        this.ringtoneDeletedHistoryRepository = ringtoneDeletedHistoryRepository;
        this.audioDurationParser = audioDurationParser;
        this.minioClient = minioClient;
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category(request.name(), request.description());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toCategoryResponse).toList();
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse createRingtone(RingtoneRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));

        AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(request.audioUrl());

        // BR-Size: must be < 50MB
        if (analysis.sizeBytes() > 50L * 1024 * 1024) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Dung lượng file vượt quá giới hạn cho phép (50MB).");
        }

        int duration = (request.durationSeconds() != null && request.durationSeconds() > 0)
            ? request.durationSeconds()
            : analysis.durationSeconds();

        // BR-Duration: must be < 5 minutes (300 seconds)
        if (duration >= 300) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Thời lượng bài nhạc không được vượt quá 5 phút.");
        }

        // BR-Vocal: must not contain vocal
        if (analysis.hasVocal()) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Nhạc có lời không được phép sử dụng. Vui lòng tải lên nhạc không lời.");
        }

        boolean status = request.status() == null || request.status();

        Ringtone ringtone = new Ringtone(
            request.title(),
            request.artistName(),
            request.audioUrl(),
            request.coverImageUrl(),
            duration,
            request.featured(),
            request.mood(),
            status,
            category
        );
        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse updateRingtone(Long id, RingtoneRequest request) {
        Ringtone ringtone = ringtoneRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (ringtone.isDeleted()) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND);
        }

        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));

        ringtone.setTitle(request.title());
        ringtone.setArtistName(request.artistName());
        ringtone.setCoverImageUrl(request.coverImageUrl());
        ringtone.setFeatured(request.featured());
        ringtone.setCategory(category);
        ringtone.setMood(request.mood());
        if (request.status() != null) {
            ringtone.setStatus(request.status());
        }

        if (request.audioUrl() != null && !request.audioUrl().equals(ringtone.getAudioUrl())) {
            ringtone.setAudioUrl(request.audioUrl());
            AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(request.audioUrl());

            // BR-Size: must be < 50MB
            if (analysis.sizeBytes() > 50L * 1024 * 1024) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Dung lượng file vượt quá giới hạn cho phép (50MB).");
            }

            int duration = (request.durationSeconds() != null && request.durationSeconds() > 0)
                ? request.durationSeconds()
                : analysis.durationSeconds();

            // BR-Duration: must be < 5 minutes (300 seconds)
            if (duration >= 300) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Thời lượng bài nhạc không được vượt quá 5 phút.");
            }

            // BR-Vocal: must not contain vocal
            if (analysis.hasVocal()) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Nhạc có lời không được phép sử dụng. Vui lòng tải lên nhạc không lời.");
            }

            ringtone.setDurationSeconds(duration);
        }

        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse updateRingtoneStatus(Long id, boolean status) {
        Ringtone ringtone = ringtoneRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (ringtone.isDeleted()) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND);
        }
        ringtone.setStatus(status);
        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @Cacheable(value = "ringtones", key = "{#searchRequest.q(), #searchRequest.categoryId(), #searchRequest.status(), #searchRequest.createdFrom(), #searchRequest.createdTo(), #searchRequest.selectionCountFrom(), #searchRequest.selectionCountTo(), #pageable.pageNumber, #pageable.pageSize}")
    @Transactional(readOnly = true)
    public PageResponse<RingtoneResponse> searchRingtones(
        RingtoneSearchRequest searchRequest,
        Pageable pageable
    ) {
        Specification<Ringtone> spec = buildSearchSpecification(searchRequest);
        return PageResponse.from(ringtoneRepository.findAll(spec, pageable).map(this::toRingtoneResponse));
    }

    @Transactional(readOnly = true)
    public byte[] exportRingtones(RingtoneSearchRequest searchRequest) {
        Specification<Ringtone> spec = buildSearchSpecification(searchRequest);
        List<Ringtone> ringtones = ringtoneRepository.findAll(spec);

        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF"); // UTF-8 BOM
        sb.append("ID,Title,Artist,Category,Mood,Duration (s),Upload Date,Selection Count,Status\n");
        for (Ringtone r : ringtones) {
            sb.append(r.getId()).append(",");
            sb.append("\"").append(escapeCsv(r.getTitle())).append("\",");
            sb.append("\"").append(escapeCsv(r.getArtistName())).append("\",");
            sb.append("\"").append(escapeCsv(r.getCategory().getName())).append("\",");
            sb.append("\"").append(escapeCsv(r.getMood())).append("\",");
            sb.append(r.getDurationSeconds()).append(",");
            sb.append(r.getCreatedAt().toString().substring(0, 10)).append(",");
            sb.append(r.getSelectionCount()).append(",");
            sb.append(r.isStatus() ? "Active" : "Inactive").append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public RingtoneStatisticsResponse getStatistics() {
        long totalTracks = ringtoneRepository.countByDeletedFalse();
        long activeTracks = ringtoneRepository.countByDeletedFalseAndStatusTrue();
        long inactiveTracks = ringtoneRepository.countByDeletedFalseAndStatusFalse();
        long activeSelections = ringtoneRepository.sumSelectionCountByDeletedFalse();
        long archivedSelections = ringtoneDeletedHistoryRepository.sumSelectionCount();
        long totalSelections = activeSelections + archivedSelections;

        return new RingtoneStatisticsResponse(totalTracks, activeTracks, inactiveTracks, totalSelections);
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public void deleteRingtone(Long id) {
        Ringtone ringtone = ringtoneRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        if (ringtone.isDeleted()) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND);
        }

        // Archive statistics
        RingtoneDeletedHistory archive = new RingtoneDeletedHistory(
            ringtone.getId(),
            ringtone.getTitle(),
            ringtone.getArtistName(),
            ringtone.getCategory().getName(),
            ringtone.getSelectionCount()
        );
        ringtoneDeletedHistoryRepository.save(archive);

        // Delete from MinIO storage
        try {
            URL url = new URL(ringtone.getAudioUrl());
            String path = url.getPath();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            int slashIdx = path.indexOf("/");
            if (slashIdx != -1) {
                String bucketName = path.substring(0, slashIdx);
                String objectKey = path.substring(slashIdx + 1);

                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectKey)
                        .build()
                );
            }
        } catch (Exception e) {
            // Keep going, but log the warning
        }

        // Hard delete metadata
        ringtoneRepository.delete(ringtone);
    }

    @Transactional(readOnly = true)
    public RingtoneResponse getRandomRingtone(String genre) {
        Optional<Ringtone> ringtone = (genre != null && !genre.isBlank())
            ? ringtoneRepository.findRandomByGenre(genre).or(ringtoneRepository::findRandom)
            : ringtoneRepository.findRandom();

        return ringtone.map(this::toRingtoneResponse)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
    }

    private Specification<Ringtone> buildSearchSpecification(RingtoneSearchRequest searchRequest) {
        return (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("deleted"), false));

            if (searchRequest.q() != null && !searchRequest.q().isBlank()) {
                String pattern = "%" + searchRequest.q().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("artistName")), pattern)
                ));
            }
            if (searchRequest.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), searchRequest.categoryId()));
            }
            if (searchRequest.status() != null) {
                predicates.add(cb.equal(root.get("status"), searchRequest.status()));
            }

            Instant fromInstant = parseDate(searchRequest.createdFrom(), false);
            if (fromInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }
            Instant toInstant = parseDate(searchRequest.createdTo(), true);
            if (toInstant != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), toInstant));
            }

            if (searchRequest.selectionCountFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("selectionCount"), searchRequest.selectionCountFrom()));
            }
            if (searchRequest.selectionCountTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("selectionCount"), searchRequest.selectionCountTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Instant parseDate(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDate localDate = LocalDate.parse(dateStr, dtf);
            LocalDateTime localDateTime = isEnd ? localDate.atTime(23, 59, 59, 999999999) : localDate.atStartOfDay();
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            try {
                LocalDate localDate = LocalDate.parse(dateStr);
                LocalDateTime localDateTime = isEnd ? localDate.atTime(23, 59, 59, 999999999) : localDate.atStartOfDay();
                return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return new CategoryResponse(
            category.getId(),
            category.getName(),
            category.getDescription(),
            category.getCreatedAt(),
            category.getUpdatedAt()
        );
    }

    private RingtoneResponse toRingtoneResponse(Ringtone ringtone) {
        return new RingtoneResponse(
            ringtone.getId(),
            ringtone.getTitle(),
            ringtone.getArtistName(),
            ringtone.getAudioUrl(),
            ringtone.getCoverImageUrl(),
            ringtone.getDurationSeconds(),
            ringtone.isFeatured(),
            ringtone.getMood(),
            ringtone.isStatus(),
            ringtone.getSelectionCount(),
            toCategoryResponse(ringtone.getCategory()),
            ringtone.getCreatedAt(),
            ringtone.getUpdatedAt()
        );
    }
}
