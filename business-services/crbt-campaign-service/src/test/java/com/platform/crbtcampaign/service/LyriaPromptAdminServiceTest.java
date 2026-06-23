package com.platform.crbtcampaign.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.platform.common.core.exception.BaseException;
import com.platform.crbtcampaign.dto.request.UpdateLyriaPromptRequest;
import com.platform.crbtcampaign.dto.response.LyriaPromptResponse;
import com.platform.crbtcampaign.dto.response.LyriaPromptVersionResponse;
import com.platform.crbtcampaign.entity.LyriaPromptConfig;
import com.platform.crbtcampaign.repository.LyriaPromptConfigRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LyriaPromptAdminServiceTest {

    private static final String CLIP = "lyria-3-clip-preview";
    private static final String VALID_TEMPLATE = "T %s %s %s %d %s %s %s %s";

    @Mock
    private LyriaPromptConfigRepository repository;

    @InjectMocks
    private LyriaPromptAdminService adminService;

    private LyriaPromptConfig config(String model, int version, String template, String status, String createdBy) {
        return new LyriaPromptConfig(model, version, template,
                List.of("C major"), List.of("piano"), List.of("fast"), List.of("studio"), status, createdBy);
    }

    private UpdateLyriaPromptRequest request(String model, String template) {
        return new UpdateLyriaPromptRequest(model, template,
                List.of("C major"), List.of("piano"), List.of("fast"), List.of("studio"));
    }

    @Test
    void getActive_emptyDb_returnsFallbackStub() {
        when(repository.findFirstByModelAndStatusOrderByVersionDesc(CLIP, "ACTIVE")).thenReturn(Optional.empty());

        LyriaPromptResponse response = adminService.getActive(CLIP);

        assertThat(response).isNotNull();
        assertThat(response.model()).isEqualTo(CLIP);
        assertThat(response.promptTemplate()).contains("You are Lyria 3");
        assertThat(response.createdBy()).isEqualTo("SYSTEM");
    }

    @Test
    void getActive_hasRow_returnsRow() {
        when(repository.findFirstByModelAndStatusOrderByVersionDesc(CLIP, "ACTIVE"))
                .thenReturn(Optional.of(config(CLIP, 4, "Custom: " + VALID_TEMPLATE, "ACTIVE", "ADMIN")));

        LyriaPromptResponse response = adminService.getActive(CLIP);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.promptTemplate()).startsWith("Custom:");
        assertThat(response.createdBy()).isEqualTo("ADMIN");
    }

    @Test
    void getActive_invalidModel_throws() {
        assertThrows(BaseException.class, () -> adminService.getActive("not-a-model"));
    }

    @Test
    void saveNewVersion_invalidPlaceholders_throwsAndNeverSaves() {
        UpdateLyriaPromptRequest invalid = request(CLIP, "only %s here");

        assertThrows(BaseException.class, () -> adminService.saveNewVersion(invalid));
        verify(repository, never()).save(any());
    }

    @Test
    void saveNewVersion_invalidModel_throws() {
        assertThrows(BaseException.class, () -> adminService.saveNewVersion(request("bad-model", VALID_TEMPLATE)));
    }

    @Test
    void saveNewVersion_valid_incrementsVersionAndDeactivatesOld() {
        LyriaPromptConfig old = config(CLIP, 2, "Old " + VALID_TEMPLATE, "ACTIVE", "ADMIN");
        when(repository.findFirstByModelAndStatusOrderByVersionDesc(CLIP, "ACTIVE")).thenReturn(Optional.of(old));
        when(repository.findTopByModelOrderByVersionDesc(CLIP)).thenReturn(Optional.of(old));
        when(repository.save(any(LyriaPromptConfig.class))).thenAnswer(i -> i.getArgument(0));

        LyriaPromptResponse response = adminService.saveNewVersion(request(CLIP, VALID_TEMPLATE));

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.model()).isEqualTo(CLIP);
        assertThat(old.getStatus()).isEqualTo("INACTIVE");
        assertThat(old.getDeactivatedAt()).isNotNull();
        verify(repository, times(2)).save(any(LyriaPromptConfig.class));
    }

    @Test
    void saveNewVersion_firstVersionWhenModelEmpty_startsAtOne() {
        when(repository.findFirstByModelAndStatusOrderByVersionDesc(CLIP, "ACTIVE")).thenReturn(Optional.empty());
        when(repository.findTopByModelOrderByVersionDesc(CLIP)).thenReturn(Optional.empty());
        when(repository.save(any(LyriaPromptConfig.class))).thenAnswer(i -> i.getArgument(0));

        LyriaPromptResponse response = adminService.saveNewVersion(request(CLIP, VALID_TEMPLATE));

        assertThat(response.version()).isEqualTo(1);
        verify(repository, times(1)).save(any(LyriaPromptConfig.class));
    }

    @Test
    void activateVersion_activatesTargetAndDeactivatesCurrent() {
        LyriaPromptConfig target = config(CLIP, 1, "V1 " + VALID_TEMPLATE, "INACTIVE", "ADMIN");
        LyriaPromptConfig current = config(CLIP, 2, "V2 " + VALID_TEMPLATE, "ACTIVE", "ADMIN");
        when(repository.findByModelAndVersion(CLIP, 1)).thenReturn(Optional.of(target));
        when(repository.findFirstByModelAndStatusOrderByVersionDesc(CLIP, "ACTIVE")).thenReturn(Optional.of(current));
        when(repository.save(any(LyriaPromptConfig.class))).thenAnswer(i -> i.getArgument(0));

        LyriaPromptResponse response = adminService.activateVersion(CLIP, 1);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(target.getDeactivatedAt()).isNull();
        assertThat(target.getActivatedAt()).isNotNull();
        assertThat(current.getStatus()).isEqualTo("INACTIVE");
        assertThat(current.getDeactivatedAt()).isNotNull();
        verify(repository, times(2)).save(any(LyriaPromptConfig.class));
    }

    @Test
    void activateVersion_unknownVersion_throws() {
        when(repository.findByModelAndVersion(CLIP, 99)).thenReturn(Optional.empty());
        assertThrows(BaseException.class, () -> adminService.activateVersion(CLIP, 99));
    }

    @Test
    void listHistory_singleModel_sortsActiveFirst() {
        LyriaPromptConfig inactive = config(CLIP, 1, "V1 " + VALID_TEMPLATE, "INACTIVE", "SYSTEM");
        LyriaPromptConfig active = config(CLIP, 2, "V2 " + VALID_TEMPLATE, "ACTIVE", "ADMIN");
        when(repository.findByModelOrderByVersionDesc(CLIP)).thenReturn(List.of(inactive, active));

        List<LyriaPromptVersionResponse> rows = adminService.listHistory(CLIP);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).status()).isEqualTo("ACTIVE");
        assertThat(rows.get(0).version()).isEqualTo(2);
    }

    @Test
    void listHistory_allModels_usesGlobalQuery() {
        when(repository.findAllByOrderByModelAscVersionDesc()).thenReturn(List.of());

        List<LyriaPromptVersionResponse> rows = adminService.listHistory("ALL");

        assertThat(rows).isEmpty();
        verify(repository).findAllByOrderByModelAscVersionDesc();
        verify(repository, never()).findByModelOrderByVersionDesc(any());
    }
}
