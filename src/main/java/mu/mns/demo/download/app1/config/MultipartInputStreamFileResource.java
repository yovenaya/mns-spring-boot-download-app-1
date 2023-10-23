package mu.mns.demo.download.app1.config;

import org.springframework.core.io.InputStreamResource;

import java.io.InputStream;

public class MultipartInputStreamFileResource extends InputStreamResource {
    private final String filename;

    public MultipartInputStreamFileResource(InputStream inputStream, String filename) {
        super(inputStream);
        this.filename = filename;
    }

    public String getFilename() {
        return this.filename;
    }

    public long contentLength() {
        return -1L;
    }
}