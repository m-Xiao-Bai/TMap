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
@TableName("system_message")
public class SystemMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 消息类型 */
    private String type;
    /** 消息标题 */
    private String title;
    /** 消息内容 */
    private String content;
    /** 关联用户ID */
    private Long userId;
    /** 关联订单ID */
    private Long orderId;
    /** 目标：1=仅用户 2=仅管理员 3=双方 */
    private Integer target;
    /** 已读标记 */
    private Integer isRead;
    /** 创建时间 */
    private LocalDateTime createTime;
}
