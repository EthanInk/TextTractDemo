package ethan.entelect.textract.demo.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Component
public class SpringAwsCredentialsProvider implements AwsCredentialsProvider {
    @Value("${cloud.aws.credentials.access-key}")
    private String accessKeyId;

    @Value("${cloud.aws.credentials.secret-key}")
    private String accessKeySecret;

    private SpringAwsCredentialsProvider() {
    }

    public static SpringAwsCredentialsProvider create() {
        return new SpringAwsCredentialsProvider();
    }

    @Override
    public AwsCredentials resolveCredentials() {
        return AwsBasicCredentials.create(accessKeyId, accessKeySecret);
    }
}
