package com.joe.task.job;

import com.joe.task.util.FileUtil_Joe;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.Charset;

/**
 * 需要增加新的Job的时候，运行这个Generator就可以了
 */
public class JobGenerator
{
    public static final String SCR_KEYWORD = "Dog";
    public static final String SRC_FILE_NAME = SCR_KEYWORD + "Job.template";

    @SneakyThrows
    public static void main(String[] args)
    {
        // TODO
        String jobKeyword = "DatabaseMonitor";

        String scrFileString = FileUtil_Joe.getResourceFileAsString(SRC_FILE_NAME);

        String destFileName = jobKeyword + "Job.java";
        String destFileString = StringUtils.replace(scrFileString, SCR_KEYWORD, jobKeyword);

        Package aPackage = JobGenerator.class.getPackage();
        System.out.println("aPackage = " + aPackage);
        String packagePath = StringUtils.remove(aPackage.toString(), "package ");

        String path = System.getProperty("user.dir");
        System.out.println("path = " + path);

        path = path + "\\src\\main\\java\\" + StringUtils.replace(packagePath, ".", File.separator);

        File destFile = new File(path, destFileName);
        if(destFile.exists())
        {
            System.out.println("destFile.getAbsolutePath() = " + destFile.getAbsolutePath());
            System.err.println("文件已经存在。。。");
        }
        else
        {
            System.out.println("destFile.getAbsolutePath() = " + destFile.getAbsolutePath());
            FileUtils.write(destFile, destFileString, Charset.defaultCharset(), false);
            System.out.println("文件生成。。。");
        }
    }
}
