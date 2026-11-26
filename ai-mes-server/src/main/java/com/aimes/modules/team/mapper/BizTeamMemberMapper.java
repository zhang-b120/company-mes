package com.aimes.modules.team.mapper;

import com.aimes.modules.team.entity.BizTeamMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BizTeamMemberMapper extends BaseMapper<BizTeamMember> {

    @Delete("DELETE FROM biz_team_member WHERE user_id = #{userId}")
    void physicalDeleteByUserId(@Param("userId") Long userId);
}
