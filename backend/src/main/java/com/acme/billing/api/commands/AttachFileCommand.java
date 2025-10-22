package com.acme.billing.api.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachFileCommand {

    @TargetAggregateIdentifier
    @NotBlank(message = "Bill ID is required")
    @Size(max = 36, message = "Bill ID must be at most 36 characters")
    private String billId;

    @NotBlank(message = "Filename is required")
    @Size(max = 255, message = "Filename must be at most 255 characters")
    private String filename;

    @NotBlank(message = "Content type is required")
    @Size(max = 100, message = "Content type must be at most 100 characters")
    private String contentType;

    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;

    private String storagePath;

    private String checksum;

    @JsonCreator
    public static AttachFileCommand create(
            @JsonProperty("billId") String billId,
            @JsonProperty("filename") String filename,
            @JsonProperty("contentType") String contentType,
            @JsonProperty("fileSize") Long fileSize,
            @JsonProperty("storagePath") String storagePath,
            @JsonProperty("checksum") String checksum) {
        return AttachFileCommand.builder()
                .billId(billId)
                .filename(filename)
                .contentType(contentType)
                .fileSize(fileSize)
                .storagePath(storagePath)
                .checksum(checksum)
                .build();
    }
}