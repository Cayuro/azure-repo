package com.ingesta.controller;

import com.ingesta.dto.EvidenciaResponse;
import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.model.Transaction;
import com.ingesta.service.EvidenciaService;
import com.ingesta.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;
    private final EvidenciaService evidenciaService;

    public TransactionController(TransactionService service, EvidenciaService evidenciaService) {
        this.service = service;
        this.evidenciaService = evidenciaService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> receive(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = service.ingest(request);
        if ("YA_RECIBIDA".equals(response.status())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getById(@PathVariable String transactionId) {
        return ResponseEntity.ok(service.getById(transactionId));
    }

    @PostMapping(value = "/{transactionId}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenciaResponse> uploadEvidencia(
            @PathVariable String transactionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        service.getById(transactionId);
        String blobName = evidenciaService.cargarEvidenciaSegura(transactionId, file.getInputStream(), file.getSize());
        return ResponseEntity.status(HttpStatus.CREATED).body(new EvidenciaResponse(transactionId, blobName));
    }
}
