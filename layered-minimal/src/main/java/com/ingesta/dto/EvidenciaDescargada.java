package com.ingesta.dto;

public record EvidenciaDescargada(
        String blobName,
        String contentType,
        byte[] content
) {
}