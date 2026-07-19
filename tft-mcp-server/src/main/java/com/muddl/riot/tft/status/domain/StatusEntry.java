package com.muddl.riot.tft.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One maintenance or incident from Riot TFT-Status-V1. Riot uses snake_case for two keys. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusEntry {
    // Boxed: Riot number fields can come back null; a primitive long fails deserialization.
    private Long id;

    @JsonProperty("maintenance_status")
    private String maintenanceStatus;

    @JsonProperty("incident_severity")
    private String incidentSeverity;

    private List<StatusContent> titles;
    private List<StatusContent> updates;
}
