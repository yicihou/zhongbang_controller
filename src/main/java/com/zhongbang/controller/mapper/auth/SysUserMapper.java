package com.zhongbang.controller.mapper.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhongbang.controller.pojo.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/** 后台员工表。登录按 username 查一行，再用 BCrypt 对密码。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
