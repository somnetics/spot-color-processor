# Spot Color Processor API

A Spring Boot-based REST API that accepts a proof file (PDF) and a logo file (PDF), extracts spot colors, and applies them to the logo. The final output is a vector-based PDF with proper spot colors compatible with Adobe Illustrator.

---

## ✨ Features

- ✅ Spot color extraction from a proof PDF
- ✅ Applies spot colors to logo (PDF)
- ✅ Output PDF is Illustrator-compatible
- ✅ Multipart form upload via REST API
- ✅ Bearer Token Authentication
- ✅ OCR via Tesseract

---

## 🔧 Dependencies

### 🧱 System

Install the following packages:

```bash
sudo apt update
sudo apt install tesseract-ocr
```

## 📦 Java Dependencies (via Maven)

Spring Boot 2.7.x

iText PDF 5.5.13.2

Apache Commons IO

## 📦 Build Instructions

### 1. Clone the repository:
```bash
git clone https://github.com/somnetics/spot-color-processor.git
cd spot-color-processor
```

### 2. Build the project with Maven:
```bash
mvn clean package
```

The JAR will be generated at: target/spotcolor-1.0.jar

## 🚀 Run as Standalone App
```bash
java -jar target/spotcolor-1.0.jar
```

The API will be available at: http://localhost:8080/api/apply-spot-colors


## 🔐 API Usage
### Endpoint:

```bash
POST /api/apply-spot-colors
```

### Headers:
```bash
Authorization: Bearer YOUR_SECRET_TOKEN

# not required if you are using Postmam
Content-Type: multipart/form-data 
```

### Required Form Fields:
- proof (PDF file)
- logo (PDF file)

### Successful Response:
- Content-Type: application/pdf
- Inline PDF download

## 🧪 Test with Postman
### Use the provided Postman collection or:
```bash
curl -X POST http://localhost:8080/api/apply-spot-colors \
  -H "Authorization: Bearer YOUR_SECRET_TOKEN" \
  -F proof=@proof.pdf \
  -F logo=@logo.pdf \
  --output output.pdf
```

## ⚙️ Run as Linux Service (Systemd)
### 1. Create a start script:
```bash
nano /opt/spotcolor/start.sh
```

```bash
#!/bin/bash
cd /opt/spotcolor
java -jar target/spotcolor-1.0.jar
```

```bash
chmod +x /opt/spotcolor/start.sh
```

### 2. Create service file:
```bash
sudo nano /etc/systemd/system/spotcolor.service
```
```bash
[Unit]
Description=Spot Color Processor Service
After=network.target

[Service]
User=ubuntu
ExecStart=/opt/spotcolor/start.sh
Restart=always

[Install]
WantedBy=multi-user.target
```

### 3. Start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable spotcolor
sudo systemctl start spotcolor
```

### 3. Check logs:
```bash
journalctl -u spotcolor -f
```

## ⚙️ Additional Linux Scripts
### 1. Build:
```bash
./build.sh
```

### 2. Start:
```bash
./start.sh
```