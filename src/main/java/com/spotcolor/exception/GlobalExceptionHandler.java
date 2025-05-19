package com.spotcolor.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<Map<String, String>> handle404(NoHandlerFoundException ex) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "Endpoint not found"));
  }
}
