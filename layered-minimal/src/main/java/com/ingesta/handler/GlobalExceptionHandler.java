package com.ingesta.handler;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.ingesta.dto.ApiErrorResponse;
import com.ingesta.exception.EvidenciaInvalidaException;
import com.ingesta.exception.FraudCaseNotFoundException;
import com.ingesta.exception.InvalidTransactionException;
import com.ingesta.exception.TransactionNotFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * Extiende ResponseEntityExceptionHandler para que las excepciones estandar de Spring MVC
 * (metodo no soportado, media type no soportado/no aceptable, parte multipart faltante,
 * ruta sin handler, etc.) se resuelvan con su codigo HTTP propio en vez de caer en el
 * catch-all de Exception.class de esta clase, que las convertia en 500.
 *
 * IMPORTANTE: MethodArgumentNotValidException, HttpMessageNotReadableException y
 * MaxUploadSizeExceededException ya estaban cubiertas aqui con @ExceptionHandler propio,
 * pero ResponseEntityExceptionHandler las maneja tambien mediante un unico metodo final
 * (handleException). Declarar @ExceptionHandler para el mismo tipo en la subclase produce
 * un error de mapeo ambiguo en arranque, asi que para esos tres casos se sobrescriben los
 * metodos protegidos correspondientes (handleMethodArgumentNotValid, etc.) en vez de usar
 * la anotacion, preservando exactamente los mismos mensajes que ya se probaban.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return badRequestObject("La transaccion no cumple el contrato", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return badRequest("La transaccion no cumple el contrato", details);
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransaction(InvalidTransactionException ex) {
        return badRequest(ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(EvidenciaInvalidaException.class)
    public ResponseEntity<ApiErrorResponse> handleEvidenciaInvalida(EvidenciaInvalidaException ex) {
        // Estos mensajes SI son de negocio y el frontend ya los muestra tal cual
        // (limite de tamano, tipo de archivo, evidencia/nombre invalido).
        return badRequest(ex.getMessage(), List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        // NO se propaga ex.getMessage(): IllegalArgumentException tambien la lanzan el SDK
        // de Azure (Cosmos, Blob Storage) y MediaType.parseMediaType, cuyos mensajes pueden
        // filtrar endpoints, nombres de contenedores o configuracion interna. Los mensajes
        // de negocio que si deben verse usan EvidenciaInvalidaException (manejador de arriba).
        return badRequest("La solicitud contiene un argumento invalido", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return badRequestObject("El archivo excede el limite permitido de 5 Megabytes.", List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            return badRequestObject("Campo no contemplado en el contrato: " + unrecognized.getPropertyName(), List.of());
        }
        return badRequestObject("El cuerpo de la peticion no tiene un formato valido", List.of());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(TransactionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(), "Transaccion no encontrada"));
    }

    @ExceptionHandler(FraudCaseNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFraudCaseNotFound(FraudCaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(), "Caso de fraude no encontrado"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        // El cuerpo que ve el cliente es deliberadamente generico para no filtrar
        // detalles internos, pero sin esta traza el error no queda registrado en
        // ninguna parte: una peticion fallida era completamente invisible en los
        // logs y por tanto imposible de diagnosticar en produccion.
        log.error("Error no controlado atendiendo la peticion", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "Ocurrio un error inesperado"));
    }

    /**
     * Punto de caida comun para el resto de excepciones que ResponseEntityExceptionHandler
     * ya resuelve con su propio codigo HTTP (405 metodo no soportado, 415 media type no
     * soportado, 406 no aceptable, 400 parte multipart faltante, 404 sin handler/recurso, etc.)
     * pero que por defecto construirian un ProblemDetail o un cuerpo vacio en vez del
     * contrato ApiErrorResponse que ya usa el resto de la API.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (request instanceof ServletWebRequest servletWebRequest) {
            HttpServletResponse response = servletWebRequest.getResponse();
            if (response != null && response.isCommitted()) {
                return null;
            }
        }

        HttpStatus resolvedStatus = HttpStatus.resolve(statusCode.value());
        String error = resolvedStatus != null ? resolvedStatus.getReasonPhrase() : String.valueOf(statusCode.value());
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Ocurrio un error al procesar la peticion";

        return new ResponseEntity<>(
                ApiErrorResponse.of(statusCode.value(), error, message, List.of()),
                headers,
                statusCode);
    }

    private ResponseEntity<ApiErrorResponse> badRequest(String message, List<String> details) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message, details));
    }

    private ResponseEntity<Object> badRequestObject(String message, List<String> details) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(), message, details));
    }
}
