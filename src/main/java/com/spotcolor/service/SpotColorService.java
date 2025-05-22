package com.spotcolor.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.CMYKColor;
import com.itextpdf.text.pdf.PRIndirectReference;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSpotColor;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.SpotColor;

@Service
public class SpotColorService {
  public List<Map<String, Object>> readSpotColors(String content, byte[] proofFile) throws IOException {
    // open input PDF
    PdfReader reader = new PdfReader(proofFile);

    // create original spot colors list
    List<Map<String, Object>> originalSpotColors = new ArrayList<>();

    // create filtered spot colors list
    List<Map<String, Object>> filteredSpotColors = new ArrayList<>();

    // loop pages
    for (int i = 1; i <= reader.getNumberOfPages(); i++) {
      PdfDictionary resources = reader.getPageN(i).getAsDict(PdfName.RESOURCES);
      extractSpotColors(reader, resources, originalSpotColors);
    }

    // convert content to uppercase
    content = content.toUpperCase();

    System.out.println(originalSpotColors);

    // loop spot colores from proof pdf
    for (int i = 0; i < originalSpotColors.size(); i++) {
      // get color object
      Map<String, Object> color = originalSpotColors.get(i);

      @SuppressWarnings("unchecked")
      ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

      // get color name
      String colorName = (String) color.get("name");

      // get segments from color name
      String[] colors = colorName.toUpperCase().split(" ");

      // loop segments of color name
      for (int c = 0; c < colors.length; c++) {
        if (colors[c].length() > 1) {
          // check if name exists in list
          int startIndex = content.indexOf(colors[c]);

          // if exists
          if (startIndex > -1) {
            // get the index of new line after the name is complete
            int endIndex = content.indexOf("\n", startIndex);

            // check line by line if name exists
            String line = content.substring(startIndex, endIndex != -1 ? endIndex : content.length());

            // get the name & cmyk valye
            Map<String, Object> entry = new HashMap<>();

            // set color name as string
            entry.put("name", line);

            // set cmyk value as array of floats
            entry.put("cmyk", cmyk);

            // finaly create the filtered stop colors
            filteredSpotColors.add(entry);
          }
        }
      }
    }

    // close the input file
    reader.close();

    // return spot colors
    return filteredSpotColors;
  }

  public ByteArrayOutputStream applySpotColors(byte[] logoFile, List<Map<String, Object>> spotColors)
      throws DocumentException, IOException {
    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // create a output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // create pdf writer
    PdfStamper stamper = new PdfStamper(reader, outputStream);

    // get ovelay content
    PdfContentByte over = stamper.getOverContent(1);

    for (int i = 0; i < spotColors.size() - 1; i++) {
      Map<String, Object> color = spotColors.get(i);

      @SuppressWarnings("unchecked")
      ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

      System.out.println(cmyk);

      // Define spot color with name and value
      PdfSpotColor spotColor = new PdfSpotColor((String) color.get("name"),
          new CMYKColor(cmyk.get(0), cmyk.get(1), cmyk.get(2), cmyk.get(0)));

      // set fill color
      over.setColorFill(new SpotColor(spotColor, 1f));

      // create a rectangle with zero pixel
      over.rectangle(0, 0, 0, 0); // Draw rectangle at (x, y, width, height)

      // fill the rectangle
      over.fill();
    }

    // close the output file
    stamper.close();

    // return outputStream
    return outputStream;
  }

  private static void extractSpotColors(PdfReader reader, PdfDictionary resources, List<Map<String, Object>> output) {
    if (resources == null)
      return;

    PdfDictionary colorSpaces = resources.getAsDict(PdfName.COLORSPACE);
    if (colorSpaces != null) {
      for (PdfName csName : colorSpaces.getKeys()) {
        PdfArray csArray = resolveColorSpaceArray(colorSpaces.get(csName));

        if (csArray != null && PdfName.SEPARATION.equals(csArray.getAsName(0))) {
          PdfName spotName = csArray.getAsName(1);
          PdfObject functionRef = csArray.getPdfObject(3);
          PdfDictionary funcDict = functionRef instanceof PRIndirectReference
              ? (PdfDictionary) PdfReader.getPdfObject(functionRef)
              : (PdfDictionary) functionRef;

          PdfArray c1 = funcDict.getAsArray(PdfName.C1);
          if (c1 != null && c1.size() == 4) {
            List<Float> cmyk = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
              cmyk.add(c1.getAsNumber(i).floatValue());
            }
            Map<String, Object> entry = new HashMap<>();
            entry.put("name", spotName.toString().replace("/", "").replace("#20", " "));
            entry.put("cmyk", cmyk);
            output.add(entry);
          }
        }
      }
    }

    PdfDictionary xObjects = resources.getAsDict(PdfName.XOBJECT);
    if (xObjects != null) {
      for (PdfName xoName : xObjects.getKeys()) {
        PdfObject xoObj = xObjects.getDirectObject(xoName);
        if (xoObj instanceof PRStream) {
          PdfDictionary xoDict = (PdfDictionary) xoObj;
          if (PdfName.FORM.equals(xoDict.getAsName(PdfName.SUBTYPE))) {
            extractSpotColors(reader, xoDict.getAsDict(PdfName.RESOURCES), output);
          }
        }
      }
    }
  }

  private static PdfArray resolveColorSpaceArray(PdfObject obj) {
    if (obj == null)
      return null;
    if (obj.isArray())
      return (PdfArray) obj;
    if (obj.isIndirect()) {
      PdfObject resolved = PdfReader.getPdfObject(obj);
      if (resolved instanceof PdfArray)
        return (PdfArray) resolved;
    }
    return null;
  }
}
