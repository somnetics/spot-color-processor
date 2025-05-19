package com.spotcolor.service;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class ImageToTextService {
  public String Convert(String imageFilePath) throws Exception {
    // create new process
    ProcessBuilder processBuilder = new ProcessBuilder(
        "tesseract",
        imageFilePath,
        "stdout",
        "-l", "eng",
        "--dpi", "96",
        "--oem", "1",
        "--psm", "3");

    // set error stream
    processBuilder.redirectErrorStream(true); // Merge stderr with stdout

    // start the process
    Process process = processBuilder.start();

    // get process output
    StringBuilder output = new StringBuilder();

    // get stdout by line
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      
      // get line by line and append to output string
      while ((line = reader.readLine()) != null) {
        output.append(line).append(System.lineSeparator());
      }
    }

    // on error
    int exitCode = process.waitFor();

    // if error
    if (exitCode != 0) {
      throw new RuntimeException("Command failed with exit code: " + exitCode);
    }

    // return output
    return output.toString().trim();
  }
}
