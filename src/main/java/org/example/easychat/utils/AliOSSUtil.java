package org.example.easychat.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Component
public class AliOSSUtil {

    @Autowired
    private OSS ossClient;

    @Value("${aliyun.oss.bucketName}")
    private String bucketName;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    /**
     * 上传图片到阿里云 OSS
     * @param file 图片文件
     * @return 图片的访问 URL
     */
    public String uploadImage(MultipartFile file) throws IOException {
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

        // 上传文件
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(new PutObjectRequest(bucketName, fileName, inputStream));
        }

        // 返回文件访问 URL
        return "https://" + bucketName + "." + endpoint + "/" + fileName;
    }
    /**
     * 上传文件
     */
    public String uploadFile(MultipartFile file) throws IOException {
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;

        // 上传文件
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(new PutObjectRequest(bucketName, fileName, inputStream));
        }

        // 返回文件访问 URL
        return "https://" + bucketName + "." + endpoint + "/" + fileName;

    }

}
