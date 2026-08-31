package com.ruoyi.waring.Util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class FileVO {
    private String virtualPath; //动态变化的路径
    private String fileName;    //文件名称  uuid.pdf
}
