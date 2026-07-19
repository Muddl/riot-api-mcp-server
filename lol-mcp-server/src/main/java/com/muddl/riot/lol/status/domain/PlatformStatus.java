package com.muddl.riot.lol.status.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A platform's operational status: current maintenances and incidents (Riot LoL-Status-V4). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformStatus {
    private String id;
    private String name;
    private List<String> locales;
    private List<StatusEntry> maintenances;
    private List<StatusEntry> incidents;
}
