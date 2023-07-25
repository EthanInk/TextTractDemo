package ethan.entelect.textract.demo.configurations;

import ethan.entelect.textract.demo.util.SpringAwsCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.textract.TextractClient;

import java.util.concurrent.Executor;

@Configuration
public class SpringBeanConfigs {
    private final SpringAwsCredentialsProvider springAwsCredentialsProvider;

    public SpringBeanConfigs(SpringAwsCredentialsProvider springAwsCredentialsProvider){
        this.springAwsCredentialsProvider = springAwsCredentialsProvider;
    }

    @Value("${cloud.aws.region.bucket}")
    private String bucketRegion;

    @Value("${cloud.aws.region.analysis}")
    private String analysisRegion;
    @Bean(destroyMethod = "close")
    public TextractClient textractClient() {
        return TextractClient.builder()
                .region(Region.of(analysisRegion))
                .credentialsProvider(springAwsCredentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(bucketRegion))
                .credentialsProvider(springAwsCredentialsProvider)
                .build();
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.initialize();
        return executor;
    }
}
