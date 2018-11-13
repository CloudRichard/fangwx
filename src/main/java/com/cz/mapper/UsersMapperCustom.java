package com.cz.mapper;

import com.cz.pojo.Users;
import com.cz.pojo.UsersExample;
import com.cz.pojo.vo.FriendRequestVO;
import com.cz.pojo.vo.MyFriendsVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UsersMapperCustom {
    public List<FriendRequestVO> queryFriendRequestList(String acceptUserId);

    public List<MyFriendsVO> queryMyFriends(String userId);

    public  void batchUpdateMsgSigned(List<String> msgIdList);
}