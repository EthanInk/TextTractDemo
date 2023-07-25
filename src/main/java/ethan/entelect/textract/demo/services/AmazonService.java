package ethan.entelect.textract.demo.services;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface AmazonService {
    Map<String, String> analyzeDocSync(MultipartFile sourceDoc);
    boolean analyzeDocAsync(MultipartFile sourceDoc);
}
