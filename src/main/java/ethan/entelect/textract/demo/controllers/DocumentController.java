package ethan.entelect.textract.demo.controllers;

import ethan.entelect.textract.demo.services.AmazonService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@AllArgsConstructor
public class DocumentController {

    private final AmazonService amazonService;

    @GetMapping
    public String helloWorld() {
        return "Hello world";
    }

    @PostMapping("/kv")
    public ResponseEntity<Map<String, String>> analyzeData(@RequestParam(name = "file") final MultipartFile file) {
        Map<String, String> data = amazonService.analyzeDocSync(file);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/kvs")
    public ResponseEntity<Map<String, String>> analyzeDataAsync(@RequestParam(name = "file") final MultipartFile file) {
        boolean success = amazonService.analyzeDocAsync(file);
        if (success)
            return ResponseEntity.ok().build();
        else
            return ResponseEntity.status(400).build();
    }
}
