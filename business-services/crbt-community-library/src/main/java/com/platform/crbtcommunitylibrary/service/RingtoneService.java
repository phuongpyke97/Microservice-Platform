package com.platform.crbtcommunitylibrary.service;

import com.platform.common.core.exception.BaseException;
import com.platform.common.core.exception.CommonErrorCode;
import com.platform.common.core.response.PageResponse;
import com.platform.crbtcommunitylibrary.dto.request.CategoryRequest;
import com.platform.crbtcommunitylibrary.dto.request.RingtoneRequest;
import com.platform.crbtcommunitylibrary.dto.response.CategoryResponse;
import com.platform.crbtcommunitylibrary.dto.response.RingtoneResponse;
import com.platform.crbtcommunitylibrary.entity.Category;
import com.platform.crbtcommunitylibrary.entity.Ringtone;
import com.platform.crbtcommunitylibrary.repository.CategoryRepository;
import com.platform.crbtcommunitylibrary.repository.RingtoneRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RingtoneService {

    private final RingtoneRepository ringtoneRepository;
    private final CategoryRepository categoryRepository;

    public RingtoneService(RingtoneRepository ringtoneRepository, CategoryRepository categoryRepository) {
        this.ringtoneRepository = ringtoneRepository;
        this.categoryRepository = categoryRepository;
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

    @Transactional
    public RingtoneResponse createRingtone(RingtoneRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));

        Ringtone ringtone = new Ringtone(
            request.title(),
            request.artistName(),
            request.audioUrl(),
            request.coverImageUrl(),
            request.durationSeconds(),
            request.featured(),
            category
        );
        return toRingtoneResponse(ringtoneRepository.save(ringtone));
    }

    @Cacheable(value = "ringtones", key = "{#query, #categoryId, #featured, #pageable.pageNumber, #pageable.pageSize}")
    @Transactional(readOnly = true)
    public PageResponse<RingtoneResponse> searchRingtones(
        String query,
        Long categoryId,
        Boolean featured,
        Pageable pageable
    ) {
        Specification<Ringtone> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("artistName")), pattern)
                ));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return PageResponse.from(ringtoneRepository.findAll(spec, pageable).map(this::toRingtoneResponse));
    }

    @Transactional(readOnly = true)
    public RingtoneResponse getRandomRingtone(String genre) {
        // T7.11: if genre specified and found, return it; otherwise fall back to global random
        Optional<Ringtone> ringtone = (genre != null && !genre.isBlank())
            ? ringtoneRepository.findRandomByGenre(genre).or(ringtoneRepository::findRandom)
            : ringtoneRepository.findRandom();

        return ringtone.map(this::toRingtoneResponse)
            .orElseThrow(() -> new BaseException(CommonErrorCode.COMMON_NOT_FOUND));
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
            toCategoryResponse(ringtone.getCategory()),
            ringtone.getCreatedAt(),
            ringtone.getUpdatedAt()
        );
    }
}
