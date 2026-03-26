package com.rag.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_course")
public class AiCourse {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("course_code")
    private String courseCode;

    @TableField("course_name")
    private String courseName;

    @TableField("category")
    private String category;

    @TableField("level_tag")
    private String levelTag;

    @TableField("keywords")
    private String keywords;

    @TableField("url")
    private String url;

    @TableField("status")
    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}

