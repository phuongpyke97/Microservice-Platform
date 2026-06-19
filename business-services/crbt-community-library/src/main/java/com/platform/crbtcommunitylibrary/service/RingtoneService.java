package com.platform.crbtcommunitylibrary.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.ApiResponse;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcommunitylibrary.client.CampaignClient;
import com.platform.crbtcommunitylibrary.client.FileServiceClient;
import com.platform.crbtcommunitylibrary.dto.request.ApproveAiToneRequest;
import com.platform.crbtcommunitylibrary.dto.request.ApproveDiyToneRequest;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.MoodRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneSearchRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.MoodResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneStatisticsResponse;
import com.platform.crbtcommunitylibrary.entity.Category;
import com.platform.crbtcommunitylibrary.entity.Mood;
import com.platform.crbtcommunitylibrary.entity.Ringtone;
import com.platform.crbtcommunitylibrary.entity.RingtoneDeletedHistory;
import com.platform.crbtcommunitylibrary.repository.CategoryRepository;
import com.platform.crbtcommunitylibrary.repository.MoodRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneDeletedHistoryRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneRepository;
import com.platform.crbtcommunitylibrary.util.AudioAnalysisResult;
import com.platform.crbtcommunitylibrary.util.AudioDurationParser;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import jakarta.persistence.criteria.Predicate;
import java.net.URI;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RingtoneService.class);

    private final RingtoneRepository ringtoneRepository;
    private final CategoryRepository categoryRepository;
    private final MoodRepository moodRepository;
    private final RingtoneDeletedHistoryRepository ringtoneDeletedHistoryRepository;
    private final AudioDurationParser audioDurationParser;
    private final MinioClient minioClient;
    private final CampaignClient campaignClient;
    private final FileServiceClient fileServiceClient;

    public RingtoneService(
        RingtoneRepository ringtoneRepository,
        CategoryRepository categoryRepository,
        MoodRepository moodRepository,
        RingtoneDeletedHistoryRepository ringtoneDeletedHistoryRepository,
        AudioDurationParser audioDurationParser,
        MinioClient minioClient,
        CampaignClient campaignClient,
        FileServiceClient fileServiceClient
    ) {
        this.ringtoneRepository = ringtoneRepository;
        this.categoryRepository = categoryRepository;
        this.moodRepository = moodRepository;
        this.ringtoneDeletedHistoryRepository = ringtoneDeletedHistoryRepository;
        this.audioDurationParser = audioDurationParser;
        this.minioClient = minioClient;
        this.campaignClient = campaignClient;
        this.fileServiceClient = fileServiceClient;
    }

    // ─── Category CRUD ────────────────────────────────────────────────────────

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category category = new Category(request.name(), request.description());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(this::toCategoryResponse).toList();
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        category.setName(request.name());
        category.setDescription(request.description());
        return toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        long count = ringtoneRepository.countByCategoryIdAndDeletedFalse(id);
        if (count > 0) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST,
                "Không thể xóa thể loại đang được sử dụng bởi " + count + " bài nhạc.");
        }
        categoryRepository.delete(category);
    }

    // ─── Mood CRUD ────────────────────────────────────────────────────────────

    @Transactional
    public MoodResponse createMood(MoodRequest request) {
        Mood mood = new Mood(request.name(), request.description());
        return toMoodResponse(moodRepository.save(mood));
    }

    @Transactional(readOnly = true)
    public List<MoodResponse> getAllMoods() {
        return moodRepository.findAll().stream().map(this::toMoodResponse).toList();
    }

    @Transactional
    public MoodResponse updateMood(Long id, MoodRequest request) {
        Mood mood = moodRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        mood.setName(request.name());
        mood.setDescription(request.description());
        return toMoodResponse(moodRepository.save(mood));
    }

    @Transactional
    public void deleteMood(Long id) {
        Mood mood = moodRepository.findById(id)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
        long count = ringtoneRepository.countByMoodIdAndDeletedFalse(id);
        if (count > 0) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST,
                "Không thể xóa tâm trạng đang được sử dụng bởi " + count + " bài nhạc.");
        }
        moodRepository.delete(mood);
    }

    // ─── Ringtone CRUD ────────────────────────────────────────────────────────

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse createRingtone(RingtoneRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Thể loại không tồn tại."));

        Mood mood = moodRepository.findById(request.moodId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Tâm trạng không tồn tại."));

        if (request.audioUrl() != null && !request.audioUrl().isBlank()) {
            Optional<Ringtone> existing = ringtoneRepository.findByAudioUrlAndDeletedFalse(request.audioUrl().trim());
            if (existing.isPresent()) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Bài nhạc này đã tồn tại trong thư viện.");
            }
        }

        AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(request.audioUrl());

        // BR-Size: must be < 50MB
        // Temporarily disable size limit checks because different .wav formats have different sizes
        // if (analysis.sizeBytes() > 50L * 1024 * 1024) {
        //     throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Dung lượng file vượt quá giới hạn cho phép (50MB).");
        // }

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
            mood,
            status,
            category
        );
        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse approveAiTone(ApproveAiToneRequest request) {
        String unifiedId = request.lyriaHistoryId();
        if (unifiedId == null || !unifiedId.startsWith("AI_")) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "ID nhạc AI không hợp lệ. Phải bắt đầu bằng 'AI_'.");
        }
        Long historyId;
        try {
            historyId = Long.parseLong(unifiedId.substring(3));
        } catch (NumberFormatException e) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "ID nhạc AI không đúng định dạng phần số.");
        }

        ApiResponse<CampaignClient.UserLyriaHistoryResponse> historyResponse = campaignClient.getLyriaHistory(historyId);
        if (historyResponse == null || !historyResponse.success() || historyResponse.data() == null) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Không tìm thấy lịch sử tạo nhạc AI tương ứng.");
        }

        CampaignClient.UserLyriaHistoryResponse history = historyResponse.data();

        if (history.audioUrl() != null && !history.audioUrl().isBlank()) {
            Optional<Ringtone> existing = ringtoneRepository.findByAudioUrlAndDeletedFalse(history.audioUrl().trim());
            if (existing.isPresent()) {
                throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Bài nhạc AI này đã được duyệt và tồn tại trong thư viện.");
            }
        }

        Category category;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Thể loại ghi đè không tồn tại."));
        } else {
            String genre = history.genre();
            category = (genre != null && !genre.isBlank())
                ? categoryRepository.findByNameIgnoreCase(genre.trim()).orElseGet(this::getRandomCategory)
                : getRandomCategory();
        }

        Mood mood;
        if (request.moodId() != null) {
            mood = moodRepository.findById(request.moodId())
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Tâm trạng ghi đè không tồn tại."));
        } else {
            String moodName = history.mood();
            mood = (moodName != null && !moodName.isBlank())
                ? moodRepository.findByNameIgnoreCase(moodName.trim()).orElseGet(this::getRandomMood)
                : getRandomMood();
        }

        AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(history.audioUrl());

        int duration = history.durationSeconds() > 0 ? history.durationSeconds() : analysis.durationSeconds();
        if (duration >= 300) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Thời lượng bài nhạc không được vượt quá 5 phút.");
        }

        if (analysis.hasVocal()) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Nhạc có lời không được phép sử dụng cho AI Composer. Vui lòng duyệt nhạc không lời.");
        }

        String title = request.title() != null && !request.title().isBlank()
            ? request.title().trim()
            : (history.title() != null && !history.title().isBlank() ? history.title().trim() : "AI Tone");
        String artistName = request.artistName() != null && !request.artistName().isBlank() ? request.artistName().trim() : "AI Composer";
        String coverImageUrl = request.coverImageUrl() != null && !request.coverImageUrl().isBlank() ? request.coverImageUrl().trim() : request.coverImageUrl();

        boolean status = request.status() == null || request.status();
        boolean featured = request.featured() != null && request.featured();

        Ringtone ringtone = new Ringtone(
            title,
            artistName,
            history.audioUrl(),
            coverImageUrl,
            duration,
            featured,
            mood,
            status,
            category
        );
        ringtone.setAiGenerated(true);

        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public RingtoneResponse approveDiyTone(ApproveDiyToneRequest request) {
        Long fileId = request.fileId();
        ApiResponse<FileServiceClient.FileMetadataResponse> fileMetadataResp = fileServiceClient.getFileMetadata(fileId);
        if (fileMetadataResp == null || !fileMetadataResp.success() || fileMetadataResp.data() == null) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Không tìm thấy file tải lên tương ứng.");
        }

        FileServiceClient.FileMetadataResponse fileMeta = fileMetadataResp.data();

        // Validate that the file belongs to media-audio-lib and is CONFIRMED
        if (!"media-audio-lib".equals(fileMeta.bucket()) || !"CONFIRMED".equals(fileMeta.status())) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "File nhạc chưa được xác nhận hoặc không hợp lệ để duyệt.");
        }

        Category category;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Thể loại ghi đè không tồn tại."));
        } else {
            category = getRandomCategory();
        }

        Mood mood;
        if (request.moodId() != null) {
            mood = moodRepository.findById(request.moodId())
                .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Tâm trạng ghi đè không tồn tại."));
        } else {
            mood = getRandomMood();
        }

        // Call file-service to copy file from media-audio-lib to media-audio bucket
        ApiResponse<String> copyResp = fileServiceClient.copyToPublic(fileId, "media-audio");
        if (copyResp == null || !copyResp.success() || copyResp.data() == null) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Lỗi sao chép file nhạc sang bucket public.");
        }
        String audioUrl = copyResp.data();

        Optional<Ringtone> existing = ringtoneRepository.findByAudioUrlAndDeletedFalse(audioUrl.trim());
        if (existing.isPresent()) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Bài nhạc này đã tồn tại trong thư viện hệ thống.");
        }

        AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(audioUrl);
        int duration = fileMeta.sizeBytes() > 0 ? analysis.durationSeconds() : 0;
        if (duration >= 300) {
            throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Thời lượng nhạc không được vượt quá 5 phút.");
        }

        String title = request.title() != null && !request.title().isBlank()
            ? request.title().trim()
            : (fileMeta.originalName() != null && !fileMeta.originalName().isBlank() ? fileMeta.originalName().trim() : "DIY Tone");
        String artistName = request.artistName() != null && !request.artistName().isBlank() ? request.artistName().trim() : "DIY Composer";
        String coverImageUrl = request.coverImageUrl() != null && !request.coverImageUrl().isBlank() ? request.coverImageUrl().trim() : request.coverImageUrl();

        boolean status = request.status() == null || request.status();
        boolean featured = request.featured() != null && request.featured();

        Ringtone ringtone = new Ringtone(
            title,
            artistName,
            audioUrl,
            coverImageUrl,
            duration,
            featured,
            mood,
            status,
            category
        );
        ringtone.setAiGenerated(false); // DIY, not generated by Lyria directly

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
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Thể loại không tồn tại."));

        Mood mood = moodRepository.findById(request.moodId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Tâm trạng không tồn tại."));

        ringtone.setTitle(request.title());
        ringtone.setArtistName(request.artistName());
        ringtone.setCoverImageUrl(request.coverImageUrl());
        ringtone.setFeatured(request.featured());
        ringtone.setCategory(category);
        ringtone.setMood(mood);
        if (request.status() != null) {
            ringtone.setStatus(request.status());
        }

        if (request.audioUrl() != null && !request.audioUrl().equals(ringtone.getAudioUrl())) {
            ringtone.setAudioUrl(request.audioUrl());
            AudioAnalysisResult analysis = audioDurationParser.analyzeAudio(request.audioUrl());

            // BR-Size: must be < 50MB
            // Temporarily disable size limit checks because different .wav formats have different sizes
            // if (analysis.sizeBytes() > 50L * 1024 * 1024) {
            //     throw new BaseException(CommonErrorCode.COMMON_BAD_REQUEST, "Dung lượng file vượt quá giới hạn cho phép (50MB).");
            // }

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

    @Cacheable(value = "ringtones", key = "{#searchRequest.q(), #searchRequest.categoryId(), #searchRequest.moodId(), #searchRequest.status(), #searchRequest.createdFrom(), #searchRequest.createdTo(), #searchRequest.selectionCountFrom(), #searchRequest.selectionCountTo(), #pageable.pageNumber, #pageable.pageSize}")
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
            sb.append("\"").append(escapeCsv(r.getMood().getName())).append("\",");
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
            ringtone.getMood().getName(),
            ringtone.getSelectionCount()
        );
        ringtoneDeletedHistoryRepository.save(archive);

        // Delete from MinIO storage
        try {
            // Use URI.getPath() which auto-decodes percent-encoding (e.g. %20 → space)
            // so the decoded key matches what MinIO SDK expects
            URI uri = new URI(ringtone.getAudioUrl());
            String path = uri.getPath();
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
            // File metadata will still be deleted; log for storage audit/cleanup
            log.warn("[deleteRingtone] Failed to delete MinIO object for ringtone id={}, url={}: {}",
                     id, ringtone.getAudioUrl(), e.getMessage());
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

    @Transactional(readOnly = true)
    public RingtoneResponse getFallbackRingtone(String genre, String mood, String instrument) {
        Optional<Ringtone> ringtone = ringtoneRepository.findRandomByGenreAndMoodAndInstrument(genre, mood, instrument);
        return ringtone.map(this::toRingtoneResponse)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
    }

    private Category getRandomCategory() {
        List<Category> all = categoryRepository.findAll();
        if (all.isEmpty()) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Không có thể loại nào trong hệ thống.");
        }
        return all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
    }

    private Mood getRandomMood() {
        List<Mood> all = moodRepository.findAll();
        if (all.isEmpty()) {
            throw new BaseException(CommonErrorCode.COMMON_NOT_FOUND, "Không có tâm trạng nào trong hệ thống.");
        }
        return all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

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
            if (searchRequest.moodId() != null) {
                predicates.add(cb.equal(root.get("mood").get("id"), searchRequest.moodId()));
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

    private MoodResponse toMoodResponse(Mood mood) {
        return new MoodResponse(
            mood.getId(),
            mood.getName(),
            mood.getDescription(),
            mood.getCreatedAt(),
            mood.getUpdatedAt()
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
            ringtone.isStatus(),
            ringtone.getSelectionCount(),
            toCategoryResponse(ringtone.getCategory()),
            toMoodResponse(ringtone.getMood()),
            ringtone.getCreatedAt(),
            ringtone.getUpdatedAt(),
            ringtone.isAiGenerated()
        );
    }

    @CacheEvict(value = "ringtones", allEntries = true)
    @Transactional
    public void incrementSelectionCountByKey(String audioFileKey) {
        if (audioFileKey == null || audioFileKey.isBlank()) {
            return;
        }
        Optional<Ringtone> ringtoneOpt = Optional.empty();
        try {
            Long id = Long.parseLong(audioFileKey);
            ringtoneOpt = ringtoneRepository.findById(id);
        } catch (NumberFormatException e) {
            if (audioFileKey.contains("/")) {
                ringtoneOpt = ringtoneRepository.findByAudioUrlAndDeletedFalse(audioFileKey);
                if (ringtoneOpt.isEmpty()) {
                    String[] parts = audioFileKey.split("/");
                    if (parts.length > 0) {
                        String filename = parts[parts.length - 1];
                        List<Ringtone> list = ringtoneRepository.findByAudioUrlContainingAndDeletedFalse(filename);
                        if (list != null && !list.isEmpty()) {
                            ringtoneOpt = Optional.of(list.get(0));
                        }
                    }
                }
            }
        }

        if (ringtoneOpt.isPresent()) {
            Ringtone ringtone = ringtoneOpt.get();
            ringtone.incrementSelectionCount();
            ringtoneRepository.save(ringtone);
            log.info("Successfully incremented selection count for Ringtone ID: {}, title: {}, new count: {}",
                    ringtone.getId(), ringtone.getTitle(), ringtone.getSelectionCount());
        } else {
            log.warn("No Ringtone found in community library matching key: {}", audioFileKey);
        }
    }
}
