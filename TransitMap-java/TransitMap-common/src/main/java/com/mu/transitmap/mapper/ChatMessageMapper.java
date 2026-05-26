package com.mu.transitmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mu.transitmap.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 从用户最近的会话消息中提取高频地点词（用于个性化快捷词）
     */
    @Select("SELECT content FROM chat_message cm " +
            "INNER JOIN chat_session cs ON cm.session_id = cs.id " +
            "WHERE cs.user_id = #{userId} AND cm.role = 'user' " +
            "ORDER BY cm.create_time DESC LIMIT #{limit}")
    List<String> findRecentUserMessages(@Param("userId") Long userId, @Param("limit") int limit);
}
