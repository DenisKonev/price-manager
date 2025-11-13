package org.qwep.qweppricemanager.mail;

import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

@Getter
public class PriceMultiPartFile implements MultipartFile {

    private final byte[] fileContent;

    private String originalFilename;

    private String contentType;

    private File file;

    private String destPath = System.getProperty("java.io.tmpdir");

    private FileOutputStream fileOutputStream;

    public PriceMultiPartFile(byte[] fileData, String name) {
        this.fileContent = fileData;
        this.originalFilename = name;
        file = new File(destPath + originalFilename);
    }

    public PriceMultiPartFile(InputStream inputStream, String name) throws RuntimeException {
        try {
            this.fileContent = inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new RuntimeException("Can't get inputStream to construct mulitpart", exception);
        }
        this.originalFilename = name;
        file = new File(destPath + originalFilename);
    }

    @Override
    public void transferTo(File dest) throws IOException, IllegalStateException {
        fileOutputStream = new FileOutputStream(dest);
        fileOutputStream.write(fileContent);
    }

    public void clearOutStreams() throws IOException {
        if (null != fileOutputStream) {
            fileOutputStream.flush();
            fileOutputStream.close();
            file.deleteOnExit();
        }
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public long getSize() {
        return 0;
    }

    @Override
    public byte[] getBytes() throws IOException {
        return fileContent;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream(fileContent);
    }
}