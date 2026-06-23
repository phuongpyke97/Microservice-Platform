package com.platform.crbtcampaign.entity;

import com.platform.crbtcampaign.entity.converter.JsonStringListConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "lyria_prompt_config")
public class LyriaPromptConfig implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    public static final String DEFAULT_MODEL = "lyria-3-clip-preview";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model", nullable = false, length = 60)
    private String model = DEFAULT_MODEL;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "keys_json", nullable = false, columnDefinition = "TEXT")
    private List<String> keys;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "secondary_instrumentations_json", nullable = false, columnDefinition = "TEXT")
    private List<String> secondaryInstrumentations;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "tempo_grooves_json", nullable = false, columnDefinition = "TEXT")
    private List<String> tempoGrooves;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "acoustic_environments_json", nullable = false, columnDefinition = "TEXT")
    private List<String> acousticEnvironments;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    public LyriaPromptConfig() {}

    public LyriaPromptConfig(String model, int version, String promptTemplate, List<String> keys,
                              List<String> secondaryInstrumentations, List<String> tempoGrooves,
                              List<String> acousticEnvironments, String status, String createdBy) {
        this.model = model;
        this.version = version;
        this.promptTemplate = promptTemplate;
        this.keys = keys;
        this.secondaryInstrumentations = secondaryInstrumentations;
        this.tempoGrooves = tempoGrooves;
        this.acousticEnvironments = acousticEnvironments;
        this.status = status;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    // Backward-compatible constructor (default model / version 1) used by fallback stubs.
    public LyriaPromptConfig(String promptTemplate, List<String> keys, List<String> secondaryInstrumentations,
                              List<String> tempoGrooves, List<String> acousticEnvironments, String status, String createdBy) {
        this(DEFAULT_MODEL, 1, promptTemplate, keys, secondaryInstrumentations, tempoGrooves, acousticEnvironments, status, createdBy);
    }

    public Long getId() { return id; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }

    public List<String> getKeys() { return keys; }
    public void setKeys(List<String> keys) { this.keys = keys; }

    public List<String> getSecondaryInstrumentations() { return secondaryInstrumentations; }
    public void setSecondaryInstrumentations(List<String> secondaryInstrumentations) { this.secondaryInstrumentations = secondaryInstrumentations; }

    public List<String> getTempoGrooves() { return tempoGrooves; }
    public void setTempoGrooves(List<String> tempoGrooves) { this.tempoGrooves = tempoGrooves; }

    public List<String> getAcousticEnvironments() { return acousticEnvironments; }
    public void setAcousticEnvironments(List<String> acousticEnvironments) { this.acousticEnvironments = acousticEnvironments; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }

    public Instant getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(Instant deactivatedAt) { this.deactivatedAt = deactivatedAt; }
}
