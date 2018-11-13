package com.cz.service.impl;

import com.cz.enums.MsgActionEnum;
import com.cz.enums.MsgSignFlagEnum;
import com.cz.enums.SearchFriendsStatusEnum;
import com.cz.mapper.*;
import com.cz.netty.ChatMsg;
import com.cz.netty.DataContent;
import com.cz.netty.UserChannelRel;
import com.cz.pojo.*;
import com.cz.pojo.vo.FriendRequestVO;
import com.cz.pojo.vo.MyFriendsVO;
import com.cz.service.UserService;
import com.cz.utils.*;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.apache.commons.lang3.StringUtils;
import org.n3r.idworker.Sid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.util.Date;
import java.util.List;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    @Autowired
    private UsersMapper userMapper;
    @Autowired
    Sid sid;
    @Autowired
    private QRCodeUtils qrCodeUtils;
    @Autowired
    private FastDFSClient fastDFSClient;
    @Autowired
    private MyFriendsMapper myFriendsMapper;
    @Autowired
    private FriendsRequestMapper friendsRequestMapper;
    @Autowired
    private UsersMapperCustom usersMapperCustom;
    @Autowired
    private ChatMsgMapper chatMsgMapper;


    @Override
    public boolean queryUsernameIsExist(String username) {
        UsersExample usersExample=new UsersExample();
        usersExample.createCriteria().andUsernameEqualTo(username);
        List<Users> users = userMapper.selectByExample(usersExample);
        return users.size()==0?false:true;
    }

    @Override
    public Users queryUserForLogin(String username, String pwd) {
        UsersExample userExample = new UsersExample();
        userExample.createCriteria().andUsernameEqualTo(username).andPasswordEqualTo(pwd);
        List<Users> users = userMapper.selectByExample(userExample);
        if (users.size()==0){
            return null;
        }else {
            return users.get(0);
        }

    }

    @Override
    public Users saveUser(Users user) {
        String userId = sid.nextShort();
        //为每个用户生成一个唯一的二维码
        String qrcodePath = "F://user"+userId+"qrcode.png";
        qrCodeUtils.createQRCode(qrcodePath,"fangwx_qrcode:"+user.getUsername());
        MultipartFile qrCodeFile = FileUtils.fileToMultipart(qrcodePath);
        String qrCodeUrl="";
        try {
            qrCodeUrl=fastDFSClient.uploadQRCode(qrCodeFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        user.setQrcode(qrCodeUrl);

        user.setId(userId);
        userMapper.insert(user);
        return user;
    }

    @Override
    public Users updateUserInfo(Users user) {
        userMapper.updateByPrimaryKeySelective(user);
        return queryUserById(user.getId());
    }

    @Override
    public Integer preconditionSearchFriends(String myUserId, String friendUsername) {
        //1.搜索的用户如果不存在,返回[无此有户]
        Users user=queryUserByUsername(friendUsername);
        if (user == null){
            return SearchFriendsStatusEnum.USER_NOT_EXIST.status;
        }
        //前置条件 - 2.搜索的用户是自己,返回[不能添加自己]
        if (user.getId().equals(myUserId)){
            return SearchFriendsStatusEnum.NOT_YOURSELF.status;
        }
        //前置条件 - 3.搜索的用户已经是好友,返回[改用户已经是你的好友]
        MyFriendsExample myFriendsExample = new MyFriendsExample();
        myFriendsExample.createCriteria().andMyUserIdEqualTo(myUserId)
                .andMyFriendUserIdEqualTo(user.getId());
        int l = (int) myFriendsMapper.countByExample(myFriendsExample);
        if (l!=0){
            return SearchFriendsStatusEnum.ALREADY_FRIENDS.status;
        }

        return SearchFriendsStatusEnum.SUCCESS.status;
    }

    public Users queryUserByUsername(String username) {
        UsersExample userExample = new UsersExample();
        userExample.createCriteria().andUsernameEqualTo(username);
        List<Users> users = userMapper.selectByExample(userExample);
        if (users.size() == 0) {
            return null;
        } else {
            return users.get(0);
        }
    }

    @Override
    public void sendFriendRequest(String myUserId, String friendUsername) {
        //根据用户名把朋友信息查询出来
        Users friend = queryUserByUsername(friendUsername);

        //1.查询发送好友请求记录表
        FriendsRequestExample fre=new FriendsRequestExample();

        fre.createCriteria().andAcceptUserIdEqualTo(friend.getId())
                            .andSendUserIdEqualTo(myUserId);
        List<FriendsRequest> friendsRequests = friendsRequestMapper.selectByExample(fre);
        if (friendsRequests.size() == 0) {
            //2.如果不是你的好友,并且好友记录没有添加,则新增好友请求记录
            String requestId=sid.nextShort();
            FriendsRequest request=new FriendsRequest();
            request.setId(requestId);
            request.setSendUserId(myUserId);
            request.setAcceptUserId(friend.getId());
            request.setRequestDateTime(new Date());
            friendsRequestMapper.insert(request);
        }

    }

    @Override
    public List<FriendRequestVO> queryFriendRequestList(String acceptUserId) {
        return usersMapperCustom.queryFriendRequestList(acceptUserId);
    }

    @Override
    public void deleteFriendRequest(String sendUserId, String acceptUserId) {
        FriendsRequestExample friendsRequestExample = new FriendsRequestExample();
        friendsRequestExample.createCriteria().andAcceptUserIdEqualTo(acceptUserId)
                .andSendUserIdEqualTo(sendUserId);
        friendsRequestMapper.deleteByExample(friendsRequestExample);
    }

    @Override
    public void passFriendRequest(String sendUserId, String acceptUserId) {
        saveFriends(sendUserId,acceptUserId);
        saveFriends(acceptUserId,sendUserId);
        deleteFriendRequest(sendUserId, acceptUserId);

        Channel sendChannel = UserChannelRel.get(sendUserId);
        if (sendChannel!=null){
            //使用websocket主动推送消息到请求发起者,更新他的通讯录到最新
            DataContent dataContent = new DataContent();
            dataContent.setAction(MsgActionEnum.PULL_FRIEND.type);
            sendChannel.writeAndFlush(new TextWebSocketFrame(JsonUtils.objectToJson(dataContent)));
        }
    }

    @Override
    public List<MyFriendsVO> queryMyFriends(String userId) {
        return usersMapperCustom.queryMyFriends(userId);
    }

    @Override
    public String saveMsg(ChatMsg chatMsg) {
        com.cz.pojo.ChatMsg msgDB = new com.cz.pojo.ChatMsg();
        String msgId = sid.nextShort();
        msgDB.setId(msgId);
        msgDB.setAcceptUserId(chatMsg.getReceiverId());
        msgDB.setSendUserId(chatMsg.getSenderId());
        msgDB.setCreateTime(new Date());
        msgDB.setSignFlag(MsgSignFlagEnum.unsign.type);
        msgDB.setMsg(chatMsg.getMsg());

        chatMsgMapper.insert(msgDB);

        return msgId;
    }

    @Override
    public void updateMsgSigned(List<String> msgIdList) {
        usersMapperCustom.batchUpdateMsgSigned(msgIdList);
    }

    @Override
    public List<com.cz.pojo.ChatMsg> getUnReadMsgList(String acceptUserId) {
        ChatMsgExample chatExample=new ChatMsgExample();
        chatExample.createCriteria().andAcceptUserIdEqualTo(acceptUserId).andSignFlagEqualTo(0);
        List<com.cz.pojo.ChatMsg> result = chatMsgMapper.selectByExample(chatExample);
        return result;
    }

    private void saveFriends(String sendUserId, String acceptUserId){
            MyFriends myFriends=new MyFriends();
            String recordID=sid.nextShort();
            myFriends.setId(recordID);
            myFriends.setMyUserId(sendUserId);
            myFriends.setMyFriendUserId(acceptUserId);
            myFriendsMapper.insert(myFriends);


    }

    private Users queryUserById(String userId){
        return userMapper.selectByPrimaryKey(userId);
    }




}
