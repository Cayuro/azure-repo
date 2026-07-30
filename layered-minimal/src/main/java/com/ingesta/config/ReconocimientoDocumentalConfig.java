package com.ingesta.config;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.storage.blob.BlobContainerClient;
import com.ingesta.repository.DatosDocumentoRepository;
import com.ingesta.service.AzureDocumentIntelligenceService;
import com.ingesta.service.DocumentoProcesadoEventPublisher;
import com.ingesta.service.LocalPdfDocumentProcessingService;
import com.ingesta.service.ReconocimientoDocumentalService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decide, via la propiedad reconocimiento.modo, cual implementacion de
 * ReconocimientoDocumentalService queda activa: Azure AI Document Intelligence, o la
 * libreria local (PDFBox + regex) para cuando el servicio de Document Intelligence no
 * esta disponible en la suscripcion (segun el informe de cuotas de semana 1). Ambas
 * comparten el mismo manejo de fallos (AbstractReconocimientoDocumentalService).
 */
@Configuration
public class ReconocimientoDocumentalConfig {

    @Bean
    @ConditionalOnProperty(name = "reconocimiento.modo", havingValue = "azure")
    public ReconocimientoDocumentalService azureDocumentIntelligenceService(
            DocumentIntelligenceClient documentIntelligenceClient,
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository datosDocumentoRepository,
            DocumentoProcesadoEventPublisher documentoProcesadoEventPublisher) {
        return new AzureDocumentIntelligenceService(
                documentIntelligenceClient, evidenciasContainerClient, datosDocumentoRepository, documentoProcesadoEventPublisher);
    }

    @Bean
    @ConditionalOnProperty(name = "reconocimiento.modo", havingValue = "local", matchIfMissing = true)
    public ReconocimientoDocumentalService localPdfDocumentProcessingService(
            BlobContainerClient evidenciasContainerClient,
            DatosDocumentoRepository datosDocumentoRepository,
            DocumentoProcesadoEventPublisher documentoProcesadoEventPublisher) {
        return new LocalPdfDocumentProcessingService(
                evidenciasContainerClient, datosDocumentoRepository, documentoProcesadoEventPublisher);
    }
}
