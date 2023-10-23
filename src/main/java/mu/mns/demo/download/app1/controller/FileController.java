package mu.mns.demo.download.app1.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import mu.mns.demo.download.app1.config.MultipartInputStreamFileResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.io.OutputStream;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final RestTemplate restTemplate;

    @Qualifier("customRestTemplate")
    private final RestTemplate customRestTemplate;

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
                httpServletResponse.addHeader("Content-Disposition", "attachment; filename=" + filename);
                httpServletResponse.addHeader("Content-Length", clientHttpResponse.getHeaders().getFirst("Content-Length"));
                StreamUtils.copy(inputStream, outputStream);
                return null;
            };

            restTemplate.execute(endpoint, HttpMethod.GET, null, responseExtractor);
        };

        return ResponseEntity.ok(streamingResponseBody);
    }

    @SneakyThrows
    @PostMapping("/upload/bad")
    public void uploadBad(@RequestParam("file") MultipartFile file) {
        String endpoint = springBootApp2Url + "/file/upload/with_multipart_file";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> request = new LinkedMultiValueMap<>();
        request.add("file", new MultipartInputStreamFileResource(file.getInputStream(), file.getOriginalFilename()));
        HttpEntity<MultiValueMap<String, Object>> httpEntity = new HttpEntity<>(request, httpHeaders);

        restTemplate.exchange(endpoint, HttpMethod.POST, httpEntity, Void.class);
    }

    @SneakyThrows
    @PostMapping("/upload/good")
    public void uploadGood(@RequestParam("file") MultipartFile file) {
        String endpoint = springBootApp2Url + "/file/upload/stream";
        InputStreamResource inputStreamResource = new InputStreamResource(file.getInputStream());

        HttpHeaders headers = new HttpHeaders();
        headers.add("filename", file.getOriginalFilename());
        HttpEntity<InputStreamResource> httpEntity = new HttpEntity<>(inputStreamResource, headers);

        customRestTemplate.exchange(endpoint, HttpMethod.POST, httpEntity, InputStreamResource.class);
    }
}
