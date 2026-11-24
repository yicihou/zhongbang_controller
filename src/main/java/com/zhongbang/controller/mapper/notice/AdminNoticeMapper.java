package com.zhongbang.controller.mapper.notice;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.notice.entity.AdminNotice;
import org.apache.ibatis.annotations.Mapper;

/** 管理端通知。新订单插入后，SSE 和轮询都读这张表。 */
@Mapper
public interface AdminNoticeMapper extends BaseMapper<AdminNotice> {
}
