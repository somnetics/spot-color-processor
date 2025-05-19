package com.spotcolor.service;

import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Service
public class PDFToImageService {
  public String Convert(InputStream pdfFilePath, String fileName) throws IOException {
    // load pdf from input stream
    PDDocument document = PDDocument.load(pdfFilePath);

    // render the pdf document
    PDFRenderer pdfRenderer = new PDFRenderer(document);

    // set output file
    File outputTiff = new File("./queue/" + fileName + ".tiff");

    // Prepare TIFF writer
    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("TIFF");
    if (!writers.hasNext()) {
      throw new IllegalStateException("No TIFF writer found (did you include TwelveMonkeys?)");
    }
    ImageWriter writer = writers.next();

    try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputTiff)) {
      // set tiff writer
      writer.setOutput(ios);
      writer.prepareWriteSequence(null);

      // loop pdf pages
      for (int page = 0; page < document.getNumberOfPages(); page++) {
        // render page
        BufferedImage renderedImage = pdfRenderer.renderImageWithDPI(page, 96);

        // convert to 1 bit black and white image
        // BufferedImage bwImage = convertTo1BitBlackWhite(renderedImage);

        // get image buffer
        IIOImage iioImage = new IIOImage(renderedImage, null, null);

        // get the writer
        ImageWriteParam param = writer.getDefaultWriteParam();

        // write the file
        writer.writeToSequence(iioImage, param);
      }

      // close writer
      writer.endWriteSequence();
    }

    // close documet
    document.close();

    // return image file path
    return outputTiff.getAbsolutePath();
  }

  private static BufferedImage convertTo1BitBlackWhite(BufferedImage source) {
    int width = source.getWidth();
    int height = source.getHeight();

    BufferedImage bw = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
    Graphics2D g2d = bw.createGraphics();

    // Convert to grayscale first
    BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    Graphics gGray = gray.getGraphics();
    gGray.drawImage(source, 0, 0, null);
    gGray.dispose();

    // Draw gray into black and white binary
    g2d.drawImage(gray, 0, 0, null);
    g2d.dispose();

    return bw;
  }
}
