package it.ness.alexander.first.service;


import io.minio.*;
import io.minio.http.Method;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.StreamingOutput;
import java.io.InputStream;

@Singleton
public class S3Client {

    private static final int BUFFER_SIZE = 1024;
    private static final long PART_SIZE = 50 * 1024 * 1024;

    @Inject
    MinioClient minioClient;

    @ConfigProperty(name = "minio.bucket-name")
    String bucketName;

    @ConfigProperty(name = "minio.folder")
    String folder;

    public S3Client() {
    }

    public String uploadObject(String filename, InputStream inputStream, String mimeType) throws Exception {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .contentType(mimeType)
                            .stream(inputStream, -1, PART_SIZE)
                            .build());
            return filename;
        } catch (Exception e) {
            throw new Exception("Failed uploading file [{0}]", e);
        }
    }

    public StreamingOutput downloadObject(String objectName) throws Exception {
        try {
            InputStream input = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return output -> {
                byte[] buffer = new byte[BUFFER_SIZE]; // Adjust if you want
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                input.close();
            };
        } catch (Exception e) {
            throw new Exception("Failed downloading object with object name [{0}]", e);
        }
    }

    public void deleteObject(String objectName) throws Exception {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            throw new Exception("Failed removing object with object name [{0}]", e);
        }
    }

    public String getPresignedObjectUrl(String objectName) throws Exception {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.DELETE)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(24 * 60 * 60)
                            .build());

        } catch (Exception e) {
            throw new Exception("Failed getting object url [{0}]", e);
        }
    }

    public String path(String filename) {
        return verifyFolder() + filename;
    }

    private String verifyFolder() {
        if (folder != null && !folder.trim().isEmpty()) {
            if (!folder.endsWith("/")) {
                return folder + "/";
            } else {
                return folder;
            }
        } else {
            return "";
        }
    }

    public void createBucket(String bucketName) throws Exception {
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }

    public boolean verifyBucket() throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }
}