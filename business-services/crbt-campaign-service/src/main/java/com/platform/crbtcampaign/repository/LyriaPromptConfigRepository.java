package com.platform.crbtcampaign.repository;

import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LyriaPromptConfigRepository extends JpaRepository<LyriaPromptConfig, Long> {

    Optional<LyriaPromptConfig> findFirstByStatusOrderByIdDesc(String status);

    Optional<LyriaPromptConfig> findFirstByModelAndStatusOrderByVersionDesc(String model, String status);

    Optional<LyriaPromptConfig> findTopByModelOrderByVersionDesc(String model);

    Optional<LyriaPromptConfig> findByModelAndVersion(String model, int version);

    List<LyriaPromptConfig> findByModelOrderByVersionDesc(String model);

    List<LyriaPromptConfig> findAllByOrderByModelAscVersionDesc();
}
