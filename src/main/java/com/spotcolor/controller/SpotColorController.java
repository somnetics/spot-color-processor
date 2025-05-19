package com.spotcolor.controller;

import com.spotcolor.service.SpotColorService;
import com.spotcolor.service.PDFToImageService;
import com.spotcolor.service.ImageToTextService;

import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SpotColorController {
  @Value("${auth.token}")
  private String authToken;

  private final SpotColorService SpotColor;
  private final PDFToImageService PDFToImage;
  private final ImageToTextService ImageToText;
  private final Tika tika;

  public SpotColorController() {
    this.SpotColor = new SpotColorService();
    this.PDFToImage = new PDFToImageService();
    this.ImageToText = new ImageToTextService();
    this.tika = new Tika();
  }

  @SuppressWarnings("null")
  @PostMapping(path = "/apply-spot-colors")
  public ResponseEntity<?> applySpotColors(
      @RequestHeader(name = "Authorization", required = false) String authHeader,
      @RequestPart(name = "proof", required = false) MultipartFile proofFile,
      @RequestPart(name = "logo", required = false) MultipartFile logoFile) throws IOException {

    // validate bearer token
    if (!("Bearer " + authToken).equals(authHeader)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
    }

    // validate proof file
    if (proofFile == null || proofFile.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Missing required file: proof"));
    }

    if (!proofFile.getOriginalFilename().endsWith(".pdf")) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type for proof"));
    }

    String proofType = this.tika.detect(proofFile.getInputStream());
    if (!proofType.equals("application/pdf")) {
      return ResponseEntity.status(415).body(Map.of("error", "Unsupported proof file type"));
    }

    // validate logo file
    if (logoFile == null || logoFile.isEmpty()) {
      return ResponseEntity.badRequest().body(Map.of("error", "Missing required file: logo"));
    }

    if (!logoFile.getOriginalFilename().endsWith(".pdf")) {
      return ResponseEntity.badRequest().body(Map.of("error", "Invalid file type for logo"));
    }

    String logoType = this.tika.detect(logoFile.getInputStream());
    if (!logoType.equals("application/pdf")) {
      return ResponseEntity.status(415).body(Map.of("error", "Unsupported logo file type"));
    }

    try {
      // get file name and extension
      int dotIndex = proofFile.getOriginalFilename().lastIndexOf('.');
      String name = proofFile.getOriginalFilename().substring(0, dotIndex);
      String extension = proofFile.getOriginalFilename().substring(dotIndex); // includes the dot

      // convert pdf to image
      InputStream inputStream = proofFile.getInputStream();
      String filepath = PDFToImage.Convert(inputStream, name);

      // convert image to text using tesseract
      String content = ImageToText.Convert(filepath);
      File file = new File(filepath);

      // after file is processed delete it if exists
      if (file.exists()) {
        if (!file.delete()) {
          throw new RuntimeException("Failed to delete the file.");
        }
      } else {
        System.err.println("File does not exist.");
        throw new RuntimeException("File does not exist.");
      }

      // get the spot colors
      List<Map<String, Object>> colors = SpotColor.readSpotColors(content, proofFile.getBytes());

      // apply the spot colors
      ByteArrayOutputStream output = SpotColor.applySpotColors(logoFile.getBytes(), colors);

      // create the pdf file content to byte
      byte[] pdfBytes = output.toByteArray();

      // create pdf respose body
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION,
              "attachment; filename=\"" + name + "_spot_color_applied" + extension + "\"")
          .contentLength(pdfBytes.length)
          .contentType(MediaType.APPLICATION_PDF)
          .body(pdfBytes);

    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Failed to process file"));
    }
  }
}
