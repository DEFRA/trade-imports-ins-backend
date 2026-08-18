package uk.gov.defra.trade.imports.ins.backend.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

@Slf4j
@Configuration
public class AwsConfig {

  @Value("${aws.region}")
  private String region;

  @Bean
  public StsClient stsClient() {
    return StsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .build();
  }
}

