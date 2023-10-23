package mu.mns.demo.download.app1.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final RestTemplate restTemplate;

    /**
     * URL for the Spring Boot App 2.
     */
    @Value("${gateway.spring-boot-app-2}")
    private String springBootApp2Url;

    /**
     * Max buffer size in RAM memory before flushing to the output stream when downloading a file.
     */
    @Value("${mns.demo.document.download.buffer-size}")
    private Integer downloadBufferSizeByte;

    /**
     * REST Endpoint to ping the Spring Boot App 1 and Spring Boot App 2.
     */
    @GetMapping
    public String hello() {
        String endpoint = springBootApp2Url + "/file";
        return restTemplate.getForObject(endpoint, String.class);
    }

    @GetMapping("/download/bad")
    public ResponseEntity<StreamingResponseBody> downloadByteArray(String filename) {
        log.info("[download-bad] Downloading the file " + filename);
        String endpoint = springBootApp2Url + "/file/download?filename=" + filename;

        ResponseEntity<byte[]> response = restTemplate.exchange(endpoint, HttpMethod.GET, null, byte[].class);
        String contentLength = response.getHeaders().getFirst("Content-Length");
        byte[] content = response.getBody();

        StreamingResponseBody streamResponse = outputStream -> {
            log.info("[download-bad] Streaming to output stream");
            outputStream.write(content);
            log.info("[download-bad] Streaming done");
        };

        // HTTP Response Header
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Content-Disposition", "attachment; filename=" + filename);
        httpHeaders.add("Content-Length", String.valueOf(contentLength));

        return ResponseEntity.ok()
                .headers(httpHeaders)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(streamResponse);
    }

    @GetMapping("/download/good")
    public ResponseEntity<StreamingResponseBody> downloadStreaming(@RequestParam String filename, HttpServletResponse httpServletResponse) {
        log.info("[download-good] Downloading the file " + filename);
        String endpoint = springBootApp2Url + "/file/download?filename=" + filename;

        StreamingResponseBody streamingResponseBody = outputStream -> {
            ResponseExtractor<OutputStream> responseExtractor = clientHttpResponse -> {
                InputStream inputStream = clientHttpResponse.getBody();
                httpServletResponse.addHeader("Content-Disposition","attachment; filename=" + filename);
                httpServletResponse.addHeader("Content-Length", clientHttpResponse.getHeaders().getFirst("Content-Length"));
                StreamUtils.copy(inputStream, outputStream);
                return null;
            };

            restTemplate.execute(endpoint, HttpMethod.GET, null, responseExtractor);
        };

        return ResponseEntity.ok(streamingResponseBody);
    }
}
