package com.dio.flagserver.entity;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Flag {
    private Long id;
    private String flagKey;
    private String name;
    private Boolean enabled;
    private String description;
    private Integer rolloutPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
