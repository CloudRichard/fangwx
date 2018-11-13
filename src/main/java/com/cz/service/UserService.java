package com.cz.service;

import com.cz.netty.ChatMsg;
import com.cz.pojo.Users;
import com.cz.pojo.vo.FriendRequestVO;
import com.cz.pojo.vo.MyFriendsVO;

import java.util.List;

public interface UserService {
        //查询用户名是否存在
        public boolean queryUsernameIsExist(String username);
        //查询用户是否存在
        public Users queryUserForLogin(String username, String pwd);
        //插入用户到表中
        public Users saveUser(Users user);
        //修改用户记录
        public Users updateUserInfo(Users user);
        //搜索好友的前置条件
        public Integer preconditionSearchFriends(String myUserId,String friendUsername);
        //根据用户名搜索好友
        public Users queryUserByUsername(String username);
        //添加好友请求记录,保存到数据库
        public void sendFriendRequest(String myUserId,String friendUsername);
        //查询好友请求
        public List<FriendRequestVO> queryFriendRequestList(String acceptUserId);
        //删除好友请求
        public void deleteFriendRequest(String sendUserId, String acceptUserId);
        //通过好友请求
        public void passFriendRequest(String sendUserId, String acceptUserId);
        //查询好友列表
        public List<MyFriendsVO> queryMyFriends(String userId);
         //保存聊天消息到数据库
        public String saveMsg(ChatMsg chatMsg);
        // 批量签收消息
        public void updateMsgSigned(List<String> msgIdList);

        //获取未签收消息列表
        public List<com.cz.pojo.ChatMsg> getUnReadMsgList(String acceptUserId);
}
