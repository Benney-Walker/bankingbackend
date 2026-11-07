package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Utility.KycUploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.core.ResponseBytes;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket-name}")
    private String bucketName;

    // The extension and content type come from the validated file contents (KycUploadRules),
    // never from the client-supplied name or header.
    public String uploadKycDocument(MultipartFile file, UUID memberId, KycUploadRules.ImageType imageType) throws IOException {
        String objectKey = "kyc/" + memberId.toString() + "/" + UUID.randomUUID() + imageType.extension();

        PutObjectRequest putOb = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(imageType.contentType())
                .build();

        s3Client.putObject(
                putOb,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()
                )
        );

        return objectKey;
    }

    /** Removes a KYC document that has been replaced by a newer upload. */
    public void deleteKycDocument(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }

    /** Reads a KYC document back from the private bucket by its object key. */
    public byte[] downloadKycDocument(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
        return objectBytes.asByteArray();
    }

}
