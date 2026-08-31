package com.ruoyi.waring.controller;

import com.ruoyi.waring.Util.FileVO;
import com.ruoyi.waring.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@CrossOrigin
public class FileController {
    @Autowired
    FileService fileService;

    @PostMapping("/upload")
    public FileVO upload(MultipartFile file) throws IOException {
        System.out.println("upload--file is" + file);
        //调用service中的业务方法
        FileVO filevo = fileService.upload(file);
        return filevo;
    }
}
