package com.spotcolor.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.io.RandomAccessSourceFactory;
import com.itextpdf.text.pdf.CMYKColor;
import com.itextpdf.text.pdf.PRIndirectReference;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PRTokeniser;
import com.itextpdf.text.pdf.PdfArray;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfContentParser;
import com.itextpdf.text.pdf.PdfDictionary;
import com.itextpdf.text.pdf.PdfFunction;
import com.itextpdf.text.pdf.PdfIndirectReference;
import com.itextpdf.text.pdf.PdfLiteral;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfSpotColor;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfString;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.RandomAccessFileOrArray;
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

    // System.out.println(content);

    // loop spot colors from proof pdf
    for (int i = 0; i < originalSpotColors.size(); i++) {
      // get color object
      Map<String, Object> color = originalSpotColors.get(i);

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
            int endIndex = content.indexOf("\r\n", startIndex);

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

    // get unique spot colors
    List<Map<String, Object>> uniqueSpotColors = filteredSpotColors.stream()
        .distinct()
        .collect(Collectors.toList());

    // return spot colors
    return uniqueSpotColors;
  }

  public List<Map<String, Object>> getAppliedSpotColors(byte[] logoFile, List<Map<String, Object>> spotColors)
      throws IOException, DocumentException {
    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // create filtered spot colors list
    List<Map<String, Object>> appliedSpotColors = new ArrayList<>();

    int xrefSize = reader.getXrefSize();
    Pattern spotColorPattern = Pattern.compile("/(Xi\\d+)\\s+cs\\s+([0-9]*\\.?[0-9]+)\\s+scn");

    for (int i = 0; i < xrefSize; i++) {
      PdfObject obj = reader.getPdfObject(i);
      if (obj == null || !obj.isStream())
        continue;

      PRStream stream = (PRStream) obj;
      byte[] data = PdfReader.getStreamBytes(stream);

      String content = new String(data, StandardCharsets.UTF_8);

      if (!content.contains(" scn"))
        continue; // Skip if no spot color

      // System.out.println("-------------------------------------");
      // System.out.println(content.substring(0));

      Matcher matcher = spotColorPattern.matcher(content);

      int index = 0;

      while (matcher.find()) {
        System.out.println("Spot Color Name: " + matcher.group(1));
        System.out.println("Tint Value: " + matcher.group(2));

        // get the name & cmyk valye
        Map<String, Object> entry = new HashMap<>();

        // set color name as string
        entry.put("spot", matcher.group(1));

        // set tint value as array of floats
        entry.put("tint", matcher.group(2));

        // set tint value as array of floats
        entry.put("name", (String) spotColors.get(index).get("name"));

        // set tint value as array of floats
        entry.put("cmyk", (ArrayList<Float>) spotColors.get(index).get("cmyk"));

        // finaly create the filtered stop colors
        appliedSpotColors.add(entry);

        // increment index
        index++;
      }
    }

    reader.close();

    // return spot colors
    return appliedSpotColors;
  }

  public ByteArrayOutputStream applySpotColorsToLayers(byte[] logoFile, List<Map<String, Object>> spotColors)
      throws IOException, DocumentException {

    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // create a output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // create pdf writer
    PdfStamper stamper = new PdfStamper(reader, outputStream);

    int xrefSize = reader.getXrefSize();
    Pattern cmykPattern = Pattern.compile("(\\d*\\.?\\d+) (\\d*\\.?\\d+) (\\d*\\.?\\d+) (\\d*\\.?\\d+) k");
    Pattern grayPattern = Pattern.compile("(\\d*\\.?\\d+) g");

    for (int i = 0; i < xrefSize; i++) {
      PdfObject obj = reader.getPdfObject(i);
      if (obj == null || !obj.isStream())
        continue;

      PRStream stream = (PRStream) obj;
      byte[] data = PdfReader.getStreamBytes(stream);

      String content = new String(data, StandardCharsets.UTF_8);

      // System.out.println("******************************************");
      // System.out.print(content.substring(0));

      if (!content.contains(" k") && !content.contains(" g"))
        continue; // Skip if no CMYK3

      Matcher matcher = null;

      if (content.contains(" k")) {
        matcher = cmykPattern.matcher(content);
      } else if (content.contains(" g")) {
        matcher = grayPattern.matcher(content);
      }

      StringBuffer modified = new StringBuffer();

      while (matcher.find()) {
        var c = Float.parseFloat(matcher.group(1));

        System.out.println(matcher.groupCount());

        if (matcher.groupCount() > 1) {
          var m = Float.parseFloat(matcher.group(2));
          var y = Float.parseFloat(matcher.group(3));
          var k = Float.parseFloat(matcher.group(4));

          System.out.println();
          System.out.println(String.format("Found CMYK: %s %s %s %s", c, m, y, k));
          System.out.println();

          for (int index = 0; index < spotColors.size(); index++) {
            Map<String, Object> color = spotColors.get(index);

            // @SuppressWarnings("unchecked")
            ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

            System.out.println(color.get("spot"));
            System.out.println(color.get("name"));
            System.out.println(cmyk);
            // cmyk.get(0)

            boolean matched = isColorClose(c, m, y, k, cmyk.get(0), cmyk.get(1), cmyk.get(2), cmyk.get(3));

            System.out.println(matched);
            System.out.println();

            // Replace with custom value: e.g., full black
            // String replacement = "1 0.72 0 0 k";
            // String replacement = "/CS1 cs /286 BLUE scn";
            if (matched) {
              // String replacement = "/Xi0 cs 1 scn";
              String replacement = String.format("/%s cs %s scn", color.get("spot"), color.get("tint"));
              matcher.appendReplacement(modified, replacement);
            }
          }
        } else {
          System.out.println();
          System.out.println(String.format("Found grayscale : %s", c));
          System.out.println();

          for (int index = 0; index < spotColors.size(); index++) {
            Map<String, Object> color = spotColors.get(index);

            // @SuppressWarnings("unchecked")
            ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

            System.out.println(color.get("spot"));
            System.out.println(color.get("name"));
            System.out.println(cmyk);
            // cmyk.get(0)

            boolean matched = isGrayScale(c, color.get("name").toString());

            // cmyk.get(2), cmyk.get(3));

            System.out.println(matched);
            System.out.println();

            // Replace with custom value: e.g., full black
            // String replacement = "1 0.72 0 0 k";
            // String replacement = "/CS1 cs /286 BLUE scn";
            if (matched) {
              // String replacement = "/Xi0 cs 1 scn";
              String replacement = String.format("/%s cs %s scn", color.get("spot"), color.get("tint"));
              matcher.appendReplacement(modified, replacement);
            }
          }
        }
      }

      matcher.appendTail(modified);
      stream.setData(modified.toString().getBytes(StandardCharsets.UTF_8));
    }

    stamper.close();
    reader.close();

    // return spot colors
    return outputStream;
  }

  public ByteArrayOutputStream apply2(byte[] logoFile) throws IOException, DocumentException {
    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // PdfStamper stamper = new PdfStamper(reader, new
    // FileOutputStream("queue/output-cmyk.pdf"));

    // create a output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    // create pdf writer
    PdfStamper stamper = new PdfStamper(reader, outputStream);

    PdfDictionary pageDict = reader.getPageN(1);
    // PdfObject obj = pageDict.get(PdfName.CONTENTS);

    Map<CMYKColor, PdfName> colorSpaceNames = new HashMap<>();
    Map<CMYKColor, PdfArray> colorSpaces = new HashMap<>();

    int csIndex = 1;

    int xrefSize = reader.getXrefSize();
    // Pattern cmykPattern = Pattern.compile("(\\d*\\.?\\d+) (\\d*\\.?\\d+)
    // (\\d*\\.?\\d+) (\\d*\\.?\\d+) k");

    for (int i = 0; i < xrefSize; i++) {
      PdfObject obj = reader.getPdfObject(i);
      if (obj == null || !obj.isStream())
        continue;

      PRStream stream = (PRStream) obj;
      byte[] data = PdfReader.getStreamBytes(stream);

      String content = new String(data, StandardCharsets.UTF_8);

      // System.out.println("-----------------------");
      // System.out.println(content);

      if (!content.contains(" k"))
        continue; // Skip if no CMYK

      // PRStream stream = (PRStream) obj;
      // byte[] data = PdfReader.getStreamBytes(stream);
      // String content = new String(data);
      StringBuilder newContent = new StringBuilder();

      String[] lines = content.split("\r?\n");

      for (String line : lines) {
        if (line.trim().endsWith("k")) {
          String[] parts = line.trim().split(" ");

          if (parts.length == 5) {
            float c = Float.parseFloat(parts[0]);
            float m = Float.parseFloat(parts[1]);
            float y = Float.parseFloat(parts[2]);
            float k = Float.parseFloat(parts[3]);

            CMYKColor cmyk = new CMYKColor(c, m, y, k);
            PdfName csName = colorSpaceNames.get(cmyk);

            // System.out.println(csName);
            if (csName == null) {
              csName = new PdfName("CS" + csIndex++);
              PdfArray separation = new PdfArray();
              separation.add(PdfName.SEPARATION);
              separation.add(new PdfName("SPOT_" + csName.toString().substring(1)));
              separation.add(PdfName.DEVICECMYK);

              PdfArray alt = new PdfArray();
              alt.add(new PdfNumber(c));
              alt.add(new PdfNumber(m));
              alt.add(new PdfNumber(y));
              alt.add(new PdfNumber(k));
              separation.add(alt);

              colorSpaceNames.put(cmyk, csName);
              colorSpaces.put(cmyk, separation);
            }

            // newContent.append(csName.toString()).append(" cs");
            // newContent.append("1 scn");
          }
        }
      }

      // newContent.append(content).append("");
      System.out.println(colorSpaceNames);
      System.out.println(colorSpaces);
      // stream.setData(newContent.toString().getBytes());

      PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
      if (resources == null) {
        resources = new PdfDictionary();
        pageDict.put(PdfName.RESOURCES, resources);
      }

      PdfDictionary csDict = resources.getAsDict(PdfName.COLORSPACE);
      if (csDict == null) {
        csDict = new PdfDictionary();
        resources.put(PdfName.COLORSPACE, csDict);
      }

      // csDict.put(new PdfName("CS1"), separation); // This is what Illustrator reads

      for (Map.Entry<CMYKColor, PdfName> entry : colorSpaceNames.entrySet()) {
        System.out.println(entry.getValue());
        System.out.println(entry.getKey());
        System.out.println(colorSpaces.get(entry.getKey()));
        // csDict.put(entry.getValue(), colorSpaces.get(entry.getKey()));
        // csDict.put(new PdfName("CS1"), colorSpaces.get(entry.getKey())); // This is
        // what Illustrator reads
      }
    }

    stamper.close();
    reader.close();

    // return spot colors
    return outputStream;
  }

  private static boolean isColorClose(float c1, float m1, float y1, float k1, float c2, float m2, float y2, float k2) {
    float threshold = 0.5f;
    return Math.abs(c1 - c2) < threshold && Math.abs(m1 - m2) < threshold && Math.abs(y1 - y2) < threshold
        && Math.abs(k1 - k2) < threshold;
  }

  private static boolean isGrayScale(float value, String name) {
    return (value == 0.0 && name.toUpperCase().equals("BLACK")) || (value == 0.5 && name.toUpperCase().equals("GRAY"))
        || (value == 1.0 && name.toUpperCase().equals("WHITE"));
  }

  public ByteArrayOutputStream applySpotColorsToDocument(byte[] logoFile, List<Map<String, Object>> spotColors)
      throws DocumentException, IOException {
    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // create a output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // create pdf writer
    PdfStamper stamper = new PdfStamper(reader, outputStream);

    // get ovelay content
    PdfContentByte over = stamper.getOverContent(1);

    for (int i = 0; i < spotColors.size(); i++) {
      Map<String, Object> color = spotColors.get(i);

      ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

      // Define spot color with name and value
      PdfSpotColor spotColor = new PdfSpotColor((String) color.get("name"),
          new CMYKColor(cmyk.get(0), cmyk.get(1), cmyk.get(2), cmyk.get(3)));

      // set fill color
      over.setColorFill(new SpotColor(spotColor, 1f));

      // create a rectangle with zero pixel
      over.rectangle(0, 0, 0, 0); // Draw rectangle at (x, y, width, height)

      // fill the rectangle
      over.fill();
    }

    // close the output file
    stamper.close();

    reader.close();

    // return outputStream
    return outputStream;
  }

  public ByteArrayOutputStream applySpotColors2(byte[] logoFile, List<Map<String, Object>> spotColors)
      throws DocumentException, IOException {
    // open input PDF
    PdfReader reader = new PdfReader(logoFile);

    // create a output stream
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

    // create pdf writer
    PdfStamper stamper = new PdfStamper(reader, outputStream);

    // get ovelay content
    PdfContentByte over = stamper.getOverContent(1);

    // for (int i = 0; i < spotColors.size(); i++) {
    // Map<String, Object> color = spotColors.get(i);

    // @SuppressWarnings("unchecked")
    // ArrayList<Float> cmyk = (ArrayList<Float>) color.get("cmyk");

    // // Define spot color with name and value
    // // PdfSpotColor spotColor = new PdfSpotColor((String) color.get("name"),
    // // new CMYKColor(cmyk.get(0), cmyk.get(1), cmyk.get(2), cmyk.get(3)));

    // // // set fill color
    // // over.setColorFill(new SpotColor(spotColor, 1f));

    // // // create a rectangle with zero pixel
    // // over.rectangle(0, 0, 0, 0); // Draw rectangle at (x, y, width, height)

    // // // fill the rectangle
    // // over.fill();

    // float[] cmykArr = new float[] {
    // cmyk.get(0), cmyk.get(1), cmyk.get(2), cmyk.get(3)
    // };

    // // PdfFunction tintTransform = PdfFunction.type2(stamper.getWriter(),
    // // new float[] { 0, 1 }, null,
    // // new float[] { 0, 0, 0, 0 },
    // // cmykArr,
    // // 1);

    // PdfObject tintTransform = PdfObject.type2(
    // stamper.getWriter(),
    // new float[] { 0f, 1f },
    // new float[] { 0f, 1f, 0f, 0f },
    // new float[] { 0f, 1f, 0f, 0f }, // CMYK when tint = 0
    // new float[] { 0f, 0.5f, 0f, 0f }, // CMYK when tint = 1
    // 1f);

    // // PdfObject tintTransform = PdfFunction.type2(stamper.getWriter(), new
    // float[] { 0 }, new float[] { 1 },
    // // new float[] { 0 },
    // // new float[] { 1 }, new float[] { 1 });

    // // Register it as an indirect object
    // PdfIndirectReference functionRef =
    // stamper.getWriter().addToBody(tintTransform).getIndirectReference();

    // PdfArray sep = new PdfArray();
    // PdfName spotName = new PdfName((String) color.get("name"));
    // sep.add(PdfName.SEPARATION);
    // sep.add(spotName);
    // sep.add(PdfName.DEVICECMYK);
    // sep.add(functionRef);

    // PdfDictionary pageDict = reader.getPageN(1);
    // PdfDictionary resources = pageDict.getAsDict(PdfName.RESOURCES);
    // if (resources == null) {
    // resources = new PdfDictionary();
    // pageDict.put(PdfName.RESOURCES, resources);
    // }

    // PdfDictionary csDict = resources.getAsDict(PdfName.COLORSPACE);
    // if (csDict == null) {
    // csDict = new PdfDictionary();
    // resources.put(PdfName.COLORSPACE, csDict);
    // }

    // PdfName csName = new PdfName("CS" + i);
    // csDict.put(csName, sep);

    // // Apply this color
    // over.setLiteral(csName + " cs\n");
    // over.setLiteral("1 scn\n");
    // over.setLiteral("0 0 0 0 re\n");
    // over.setLiteral("f\n");
    // }

    // close the output file
    stamper.close();

    reader.close();

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
