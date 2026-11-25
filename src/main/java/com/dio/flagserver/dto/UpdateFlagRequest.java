package com.dio.flagserver.dto;

import lombok.Data;

@Data
public class UpdateFlagRequest {
    private String name;
    private Boolean enabled;
    private String description;
    private Integer rolloutPercent;
}
