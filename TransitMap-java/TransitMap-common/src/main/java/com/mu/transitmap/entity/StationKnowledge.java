package com.mu.transitmap.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("station_knowledge")
public class StationKnowledge implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long cityId;
    private Long stationId;
    private Long lineId;
    private String title;
    private String content;
    private String keywords;
    private String category;
    private Integer priority;
    private String embedding;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
