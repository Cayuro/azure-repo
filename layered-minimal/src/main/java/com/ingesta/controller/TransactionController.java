package com.ingesta.controller;

import com.ingesta.dto.EvidenciaResponse;
import com.ingesta.dto.RiesgoResponse;
import com.ingesta.dto.TransactionRequest;
import com.ingesta.dto.TransactionResponse;
import com.ingesta.model.DatosDocumento;
import com.ingesta.model.Transaction;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.service.DocumentIntelligenceService;
import com.ingesta.service.EvidenciaService;
import com.ingesta.service.TransactionScoringService;
import com.ingesta.service.TransactionService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService service;
    private final EvidenciaService evidenciaService;
    private final TransactionScoringService scoringService;
    private final DocumentIntelligenceService documentIntelligenceService;
    private final DatosDocumentoRepository datosDocumentoRepository;

    public TransactionController(
            TransactionService service,
            EvidenciaService evidenciaService,
            TransactionScoringService scoringService,
            DocumentIntelligenceService documentIntelligenceService,
            DatosDocumentoRepository datosDocumentoRepository) {
        this.service = service;
        this.evidenciaService = evidenciaService;
        this.scoringService = scoringService;
        this.documentIntelligenceService = documentIntelligenceService;
        this.datosDocumentoRepository = datosDocumentoRepository;
    }

    @Operation(summary = "Recibe una transaccion")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Transaccion recibida"),
            @ApiResponse(responseCode = "200", description = "Transaccion duplicada"),
            @ApiResponse(responseCode = "400", description = "Contrato invalido")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> receive(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse response = service.ingest(request);
        if ("YA_RECIBIDA".equals(response.status())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Consulta una transaccion por id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaccion encontrada"),
            @ApiResponse(responseCode = "404", description = "Transaccion no encontrada")
    })
    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getById(@PathVariable String transactionId) {
        return ResponseEntity.ok(service.getById(transactionId));
    }

    @Operation(summary = "Lista todas las transacciones")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Listado devuelto")})
    @GetMapping
    public ResponseEntity<List<Transaction>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @Operation(summary = "Consulta el riesgo calculado de una transaccion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Riesgo devuelto"),
            @ApiResponse(responseCode = "404", description = "Transaccion no encontrada")
    })
    @GetMapping("/{transactionId}/riesgo")
    public ResponseEntity<RiesgoResponse> getRiesgo(@PathVariable String transactionId) {
        service.getById(transactionId);
        return ResponseEntity.ok(scoringService.obtenerRiesgo(transactionId));
    }

    @Operation(summary = "Consulta los datos estructurados extraidos de las evidencias de una transaccion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Datos de documento devueltos"),
            @ApiResponse(responseCode = "404", description = "Aun no hay datos extraidos para la transaccion")
    })
    @GetMapping("/{transactionId}/datos-documento")
    public ResponseEntity<DatosDocumento> getDatosDocumento(@PathVariable String transactionId) {
        return datosDocumentoRepository.findByTransactionId(transactionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Lista las evidencias de una transaccion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado devuelto"),
            @ApiResponse(responseCode = "404", description = "Transaccion no encontrada")
    })
    @GetMapping("/{transactionId}/evidencias")
    public ResponseEntity<List<String>> listEvidencias(@PathVariable String transactionId) {
        service.getById(transactionId);
        return ResponseEntity.ok(evidenciaService.listEvidencias(transactionId));
    }

    @Operation(summary = "Descarga una evidencia por su nombre")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evidencia devuelta"),
            @ApiResponse(responseCode = "400", description = "Nombre invalido"),
            @ApiResponse(responseCode = "404", description = "Evidencia no encontrada")
    })
    @GetMapping(value = "/{transactionId}/evidencias/{blobName:.+}")
    public ResponseEntity<byte[]> downloadEvidencia(@PathVariable String transactionId, @PathVariable String blobName) throws IOException {
        service.getById(transactionId);
        var evidencia = evidenciaService.descargarEvidencia(transactionId, blobName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidencia.contentType()))
                .header("Content-Disposition", "inline; filename=\"" + evidencia.blobName() + "\"")
                .body(evidencia.content());
    }

    @PostMapping(value = "/{transactionId}/evidencias", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenciaResponse> uploadEvidencia(
            @PathVariable String transactionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        service.getById(transactionId);
        String blobName = evidenciaService.cargarEvidenciaSegura(transactionId, file.getInputStream(), file.getSize());
        documentIntelligenceService.extraerYAdjuntar(transactionId, blobName);
        return ResponseEntity.status(HttpStatus.CREATED).body(new EvidenciaResponse(transactionId, blobName));
    }
}
