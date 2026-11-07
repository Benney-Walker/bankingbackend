package com.bynexlabs.bankingbackend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.bynexlabs.bankingbackend.Utility.KycUploadRules;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StorageService storageService;

    @Test
    void uploadUsesTheDetectedTypeNotTheClientsNameOrHeader() throws Exception {
        ReflectionTestUtils.setField(storageService, "bucketName", "kyc-documents");
        UUID memberId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "card.gif", "image/gif", new byte[]{1, 2, 3});

        String key = storageService.uploadKycDocument(file, memberId, KycUploadRules.ImageType.PNG);

        assertThat(key).startsWith("kyc/" + memberId + "/").endsWith(".png");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("kyc-documents");
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    // Regression test for 3.1: the old download read the bucket from a static @Value field,
    // which Spring never sets, so every automated KYC check failed.
    @Test
    void downloadUsesTheConfiguredBucket() {
        ReflectionTestUtils.setField(storageService, "bucketName", "kyc-documents");
        byte[] content = {7, 8, 9};
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), content));

        byte[] downloaded = storageService.downloadKycDocument("kyc/m1/doc.jpg");

        assertThat(downloaded).isEqualTo(content);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo("kyc-documents");
        assertThat(request.getValue().key()).isEqualTo("kyc/m1/doc.jpg");
    }
}
