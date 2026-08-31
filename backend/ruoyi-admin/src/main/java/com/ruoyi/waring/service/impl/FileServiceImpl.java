package com.ruoyi.waring.service.impl;

import com.ruoyi.waring.Util.FileVO;
import com.ruoyi.waring.service.FileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
//加载指定的配置文件
@PropertySource("classpath:/properties/image.properties")
public class FileServiceImpl implements FileService {

    //获取配置文件中的配置 为属性动态赋值 注解@Value
    @Value("${image.localPathDir}")
    private String localPathDir;  // Windows路径 例如 D:/files
    @Value("${image.localUrlPath}")
    private String localUrlPath;  // Linux路径 例如 /Users/zhaoguoxing/Desktop/files

    @Override
    public FileVO upload(MultipartFile file) throws IOException {
        //1.1 获取文件名称
        String fileName = file.getOriginalFilename();

        //2. 目录结构
        //2.1 实现分目录存储  可以以时间维度年月日进行分隔 /yyyy/MM/dd/
        String datePath =
                new SimpleDateFormat("/yyyy/MM/dd/").format(new Date());
        //2.2 最终本地图片存储路径
        //    进行目录的拼接  "/Users/zhaoguoxing/Desktop/files/2022/03/22";
        String localDir = localUrlPath + datePath;
        //2.3 需要创建目录
        File dirFile = new File(localDir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        //3.文件名称重复  采用UUID防止文件重名 uuid.pdf
        String uuid = UUID.randomUUID().toString()
                .replace("-", "");
        //3.1.获取文件类型
        //fileName = abc.jpg  fileType=.pdf
        String fileType =
                fileName.substring(fileName.lastIndexOf("."));
        //3.2.重新拼接文件名  uuid.pdf
        String realFileName = uuid + fileType;

        //4.最终文件存储的路径+文件名 = /2021/11/11/uuid.pdf
        //可以在这里将路径存储到数据库 实际保存文件地址 此处省略
        String filePathAll = localDir + realFileName;
        //5.实现文件上传
        File realFile = new File(filePathAll);
        file.transferTo(realFile);

        //6.封装FileVO对象  //2021/11/11/uuid.pdf 图片路径 稍后给前台传递
        //我们不可能将filePathAll告诉用户，这样不安全，容易被攻击
        //virtualPath 半个路径，没有具体盘符或根目录 /2021/11/11/uuid.pdf
        String virtualPath = datePath + realFileName;
        //7.将文件存储路径(半个路径，没有具体盘符或根目录) 和 重命名后的文件名 封装到实体类中
        return new FileVO(virtualPath, realFileName);
    }
}
