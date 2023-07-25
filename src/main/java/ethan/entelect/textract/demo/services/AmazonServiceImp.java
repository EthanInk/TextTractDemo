package ethan.entelect.textract.demo.services;

import ethan.entelect.textract.demo.util.KeyValueSet;
import ethan.entelect.textract.demo.util.TextractDocument;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.io.IOException;
import java.util.*;

@Getter
@Setter
@Service
public class AmazonServiceImp implements AmazonService {
    private final TextractClient textractClient;
    private final S3Client s3Client;

    public AmazonServiceImp(TextractClient textractClient, S3Client s3Client) {
        this.textractClient = textractClient;
        this.s3Client = s3Client;
    }

    @Value("${cloud.aws.bucket.name}")
    private String BUCKET_NAME;

    @Override
    public Map<String, String> analyzeDocSync(MultipartFile sourceDoc) {
        try {
            SdkBytes sourceBytes = SdkBytes.fromInputStream(sourceDoc.getInputStream());

            // Get the input Document object as bytes
            Document myDoc = Document.builder()
                    .bytes(sourceBytes)
                    .build();

            List<FeatureType> featureTypes = new ArrayList<FeatureType>();
            featureTypes.add(FeatureType.FORMS);

            AnalyzeDocumentRequest analyzeDocumentRequest = AnalyzeDocumentRequest.builder()
                    .featureTypes(featureTypes)
                    .document(myDoc)
                    .build();

            AnalyzeDocumentResponse analyzeDocument = textractClient.analyzeDocument(analyzeDocumentRequest);
            List<Block> docInfo = analyzeDocument.blocks();

            parseBlocksForFormData(docInfo);
            return null;
        } catch (TextractException | IOException e) {
            System.out.println("Yikes file broke");
            System.out.println(e);
            return null;
        }
    }

    @Override
    public boolean analyzeDocAsync(MultipartFile sourceDoc) {
        try {
            //upload to s3
            System.out.println("Uploading...");
            String docKey = uploadDocToS3(sourceDoc);
            if (docKey == null) return false;
            System.out.println("Done.");
            continueDocAnalyzeAsync(docKey);
            return true;
        } catch (Exception e) {
            System.out.println(e);
            return false;
        }
    }

    @Async
    private void continueDocAnalyzeAsync(String docKey){
        //start analyse
        System.out.println("Starting analysis...");
        String jobId = startS3DocAnalyze(docKey);
        if (jobId == null) return;
        System.out.println("Started analysis.");
        //get analysis
        System.out.println("Getting report...");
        List<Block> docInfo = getS3DocAnalyze(jobId);
        if (docInfo == null) return;
        System.out.println("Report collected.");
        //print results
        System.out.println("Parsing blocks...");
        parseBlocksForFormData(docInfo);
        System.out.println("Parsing done.");
        //delete file
        System.out.println("Removing file...");
        boolean deleteSuccess = deleteDocOnS3(docKey);
        if (deleteSuccess) {
            System.out.println("File removed.");
        } else {
            System.out.println("File not removed.");
        }
    }

    private String uploadDocToS3(MultipartFile sourceDoc) {
        try {
            String key = UUID.randomUUID().toString() + ".pdf";
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(key)
                    .build();
            PutObjectResponse response = s3Client.putObject(objectRequest, RequestBody.fromBytes(sourceDoc.getBytes()));
            return key;

        } catch (Exception e) {
            System.out.println(e);
            return null;
        }
    }

    private boolean deleteDocOnS3(String docKey) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(docKey)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            return true;
        } catch (Exception e) {
            System.out.println(e);
            return false;
        }
    }

    private String startS3DocAnalyze(String docKey) {
        try {
            List<FeatureType> myList = List.of(FeatureType.TABLES, FeatureType.FORMS);

            S3Object s3Object = S3Object.builder()
                    .bucket(BUCKET_NAME)
                    .name(docKey)
                    .build();

            DocumentLocation location = DocumentLocation.builder()
                    .s3Object(s3Object)
                    .build();

            StartDocumentAnalysisRequest documentAnalysisRequest = StartDocumentAnalysisRequest.builder()
                    .documentLocation(location)
                    .featureTypes(myList)
                    .build();

            StartDocumentAnalysisResponse response = textractClient.startDocumentAnalysis(documentAnalysisRequest);

            // Get the job ID
            return response.jobId();

        } catch (TextractException e) {
            System.err.println(e);
            return null;
        }
    }

    private List<Block> getS3DocAnalyze(String jobId) {
        int index = 0;
        int pageIndex = 1;
        String nextToken = null;
        String status = "";
        List<Block> blocks = new ArrayList<>();
        List<GetDocumentAnalysisResponse> responses = new ArrayList<>();
        try {
            while (true) {
                GetDocumentAnalysisRequest analysisRequest = GetDocumentAnalysisRequest.builder()
                        .jobId(jobId)
                        .maxResults(1000)
                        .nextToken(nextToken)
                        .build();

                GetDocumentAnalysisResponse response = textractClient.getDocumentAnalysis(analysisRequest);
                status = response.jobStatus().toString();

                if (status.compareTo("SUCCEEDED") == 0) {
                    blocks.addAll(response.blocks());
                    responses.add(response);
                    if (response.nextToken() != null){
                        nextToken = response.nextToken();
                        System.out.println("Page " + pageIndex + " collected.");
                        pageIndex++;
                        index = 0;
                    }else {
                        TextractDocument textractDocument = new TextractDocument(responses);
                        return blocks;
                    }
                } else {
                    System.out.println(index + " status is: " + status);
                    Thread.sleep(1000);
                }
                index++;
            }
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    private void parseBlocksForFormData(List<Block> blocks) {
        Map<String, Block> words = new HashMap<>();
        Map<String, KeyValueSet> keyValueSet = new HashMap<>();

        for (Block block : blocks) {
            if (block.blockType().equals(BlockType.WORD)) {
                words.put(block.id(), block);
            } else if (block.blockType().equals(BlockType.KEY_VALUE_SET)) {
                if (block.entityTypes().contains(EntityType.KEY)) {
                    Optional<Relationship> relationshipsOfBlockOfTypeValue = block.relationships().stream().filter(relationship -> relationship.type().equals(RelationshipType.VALUE)).findFirst();
                    if (relationshipsOfBlockOfTypeValue.isEmpty()) continue;
                    String id = relationshipsOfBlockOfTypeValue.get().ids().get(0);
                    if (keyValueSet.containsKey(id)) {
                        keyValueSet.get(id).setKey(block);
                    } else {
                        keyValueSet.put(id, KeyValueSet.builder().key(block).build());
                    }
                } else if (block.entityTypes().contains(EntityType.VALUE)) {
                    if (keyValueSet.containsKey(block.id())) {
                        keyValueSet.get(block.id()).setValue(block);
                    } else {
                        keyValueSet.put(block.id(), KeyValueSet.builder().value(block).build());
                    }
                }
            }
        }

        for (KeyValueSet formKeyValueSet : keyValueSet.values()) {
            if (!formKeyValueSet.isComplete()) continue;
            List<Relationship> relationshipsOfKeyOfTypeChild = formKeyValueSet.getKey().relationships().stream().filter(relationship -> relationship.type().equals(RelationshipType.CHILD)).toList();
            if (!relationshipsOfKeyOfTypeChild.isEmpty()) {
                relationshipsOfKeyOfTypeChild.forEach(child -> {
                    child.ids().forEach(childId -> {
                        formKeyValueSet.addKeyWord(words.get(childId));
                    });
                });
            }
            List<Relationship> relationshipsOfValueOfTypeChild = formKeyValueSet.getValue().relationships().stream().filter(relationship -> relationship.type().equals(RelationshipType.CHILD)).toList();
            if (!relationshipsOfValueOfTypeChild.isEmpty()) {
                relationshipsOfValueOfTypeChild.forEach(child -> {
                    child.ids().forEach(childId -> {
                        formKeyValueSet.addValueWord(words.get(childId));
                    });
                });
            }
        }

        for (KeyValueSet formKeyValueSet : keyValueSet.values()) {
            System.out.print("[");
            System.out.print(formKeyValueSet.keyWordsToString());
            System.out.print(": ");
            System.out.print(formKeyValueSet.valueWordsToString());
            System.out.println("]");
        }
    }
}
