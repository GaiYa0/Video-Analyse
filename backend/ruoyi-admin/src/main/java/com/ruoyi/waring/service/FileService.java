package com.ruoyi.waring.service;

import com.ruoyi.waring.Util.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    //文件上传
    public FileVO upload(MultipartFile file) throws IOException;
}
