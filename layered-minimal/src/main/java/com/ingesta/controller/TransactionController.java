package com.ingesta.controller;

import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.model.Transaction;
import com.ingesta.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
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
}
