package com.ingesta.controller;

import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.model.Transaction;
import com.ingesta.service.TransactionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.Instant;

@Controller
public class WebController {

    private final TransactionService transactionService;

    public WebController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/")
    public String home(Model model) {
        return "index";
    }

    @PostMapping("/")
    public String createTransaction(@RequestParam String transactionId,
                                    @RequestParam String accountId,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam String currency,
                                    @RequestParam String occurredAt,
                                    @RequestParam Double latitude,
                                    @RequestParam Double longitude,
                                    @RequestParam String merchantId,
                                    @RequestParam String merchantCategory,
                                    Model model) {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionId(transactionId);
        request.setAccountId(accountId);
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setOccurredAt(Instant.parse(occurredAt));
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setMerchantId(merchantId);
        request.setMerchantCategory(merchantCategory);

        try {
            TransactionResponse response = transactionService.ingest(request);
            model.addAttribute("message", "Transacción creada correctamente: " + response.getStatus());
            model.addAttribute("error", false);
        } catch (Exception ex) {
            model.addAttribute("message", ex.getMessage());
            model.addAttribute("error", true);
        }

        return "index";
    }

    @GetMapping("/transaction")
    public String getTransaction(@RequestParam String transactionId, Model model) {
        try {
            Transaction transaction = transactionService.getById(transactionId);
            model.addAttribute("transaction", transaction);
        } catch (Exception ex) {
            model.addAttribute("message", ex.getMessage());
            model.addAttribute("error", true);
        }
        return "index";
    }
}
