package com.cz.controller;

import com.cz.enums.OperatorFriendRequestTypeEnum;
import com.cz.enums.SearchFriendsStatusEnum;
import com.cz.pojo.Users;
import com.cz.pojo.bo.UsersBO;
import com.cz.pojo.vo.MyFriendsVO;
import com.cz.pojo.vo.UsersVO;
import com.cz.service.UserService;
import com.cz.utils.FastDFSClient;
import com.cz.utils.FileUtils;
import com.cz.utils.JSONResult;
import com.cz.utils.MD5Utils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("u")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private FastDFSClient fastDFSClient;


    @PostMapping("/registOrLogin")
    public JSONResult registOrLogin(@RequestBody Users user) throws Exception {
        //0.判断用户名密码是否为空
        if (StringUtils.isBlank(user.getUsername())||StringUtils.isBlank(user.getPassword())){
            return JSONResult.errorMsg("用户名或密码不能为空");
        }
        //1.判断用户名是否存在,存在就登录,不存在就注册
        boolean usernameIsExist = userService.queryUsernameIsExist(user.getUsername());
        Users userResult;
        if (usernameIsExist){
            //1.1登录
            userResult= userService.queryUserForLogin(user.getUsername(), MD5Utils.getMD5Str(user.getPassword()));
            if (userResult==null){
                return JSONResult.errorMsg("用户名或密码不正确");
            }
        }else {
            //1.2注册
            user.setNickname(user.getUsername());
            user.setFaceImage("");
            user.setFaceImageBig("");
            user.setPassword(MD5Utils.getMD5Str(user.getPassword()));
            userResult = userService.saveUser(user);
        }
        UsersVO userVO=new UsersVO();
        BeanUtils.copyProperties(userResult,userVO);

        return  JSONResult.ok(userVO);
    }

    @PostMapping("/uploadFaceBase64")
    public JSONResult uploadFaceBase64(@RequestBody UsersBO userBO) throws Exception {
        //获取前端传来的base64字符串,然后转换为文件对象再上传
        String base64Data = userBO.getFaceData();
        String userFacePath = "F:\\"+userBO.getUserId()+"userface64.png";
        FileUtils.base64ToFile(userFacePath,base64Data);
        MultipartFile multipartFile = FileUtils.fileToMultipart(userFacePath);
        String url = fastDFSClient.uploadBase64(multipartFile);


        //获取缩略图的url
        String thump="_80x80.";
        String arr[]=url.split("\\.");
        String thumpImgUrl=arr[0]+thump+arr[1];

        Users user=new Users();
        user.setId(userBO.getUserId());
        user.setFaceImage(thumpImgUrl);
        user.setFaceImageBig(url);
        Users result = userService.updateUserInfo(user);

        return  JSONResult.ok(result);
    }

//    设置昵称
    @PostMapping("/setNickname")
    public JSONResult setNickname(@RequestBody UsersBO userBO){
        Users user=new Users();
        user.setId(userBO.getUserId());
        user.setNickname(userBO.getNickname());
        Users result = userService.updateUserInfo(user);
        return JSONResult.ok(result);
    }

    /**
     *搜索好友接口,根据账号做匹配查询,而不是模糊查询
     */
    @PostMapping("/search")
    public JSONResult searchUser(String myUserId,String friendUsername){

        //0.判断myUserId friendUsername不能为空
        if (StringUtils.isBlank(myUserId)||StringUtils.isBlank(friendUsername)){
            return JSONResult.errorMsg("");
        }
        //前置条件 - 1.搜索的用户如果不存在,返回[无此用户]
        //前置条件 - 1.搜索的用户是自己,返回[不能条件自己]
        //前置条件 - 1.搜索的用户已经是好友,返回[改用户已经是你的好友]
        Integer status = userService.preconditionSearchFriends(myUserId, friendUsername);
        if (status == SearchFriendsStatusEnum.SUCCESS.status){
            Users user = userService.queryUserByUsername(friendUsername);
            UsersVO usersVO=new UsersVO();
            BeanUtils.copyProperties(user,usersVO);
            return JSONResult.ok(usersVO);
        }else {
            String errMsg = SearchFriendsStatusEnum.getMsgByKey(status);
            return JSONResult.errorMsg(errMsg);
        }
    }

    /**
     * 添加好友的请
     */
    @PostMapping("/addFriendRequest")
    public JSONResult addFriendRequest(String myUserId,String friendUsername){

        //0.判断myUserId friendUsername不能为空
        if (StringUtils.isBlank(myUserId)||StringUtils.isBlank(friendUsername)){
            return JSONResult.errorMsg("");
        }
        //前置条件 - 1.搜索的用户如果不存在,返回[无此用户]
        //前置条件 - 2.搜索的用户是自己,返回[不能添加自己]
        //前置条件 - 3.搜索的用户已经是好友,返回[改用户已经是你的好友]
        Integer status = userService.preconditionSearchFriends(myUserId, friendUsername);
        if (status == SearchFriendsStatusEnum.SUCCESS.status){
            userService.sendFriendRequest(myUserId,friendUsername);
        }else {
            String errMsg = SearchFriendsStatusEnum.getMsgByKey(status);
            return JSONResult.errorMsg(errMsg);
        }
        return JSONResult.ok();
    }

    @PostMapping("/queryFriendRequests")
    public JSONResult queryFriendRequests(String userId){

        //0.判断myUserId friendUsername不能为空
        if (StringUtils.isBlank(userId)){
            return JSONResult.errorMsg("");
        }
        //1.查询用户接受到的朋友申请
        return JSONResult.ok(userService.queryFriendRequestList(userId));
    }

    //接受方  通过或忽略好友请求
    @PostMapping("/operFriendRequest")
    public JSONResult operFriendRequest(String acceptUserId, String sendUserId,Integer operType){
        // 0. acceptUserId sendUserId operType 判断不能为空
        if (StringUtils.isBlank(acceptUserId)
                || StringUtils.isBlank(sendUserId)
                || operType == null) {
            return JSONResult.errorMsg("");
        }

        // 1. 如果operType 没有对应的枚举值，则直接抛出空错误信息
        if (StringUtils.isBlank(OperatorFriendRequestTypeEnum.getMsgByType(operType))) {
            return JSONResult.errorMsg("");
        }

        if (operType == OperatorFriendRequestTypeEnum.IGNORE.type) {
            // 2. 判断如果忽略好友请求，则直接删除好友请求的数据库表记录
            userService.deleteFriendRequest(sendUserId, acceptUserId);
        } else if (operType == OperatorFriendRequestTypeEnum.PASS.type) {
            // 3. 判断如果是通过好友请求，则互相增加好友记录到数据库对应的表
            //	   然后删除好友请求的数据库表记录
            userService.passFriendRequest(sendUserId, acceptUserId);
        }
        // 1. 数据库查询好友列表
        List<MyFriendsVO> myFirends = userService.queryMyFriends(acceptUserId);

        return JSONResult.ok(myFirends);
    }

    /**
     * @Description: 查询我的好友列表
     */
    @PostMapping("/myFriends")
    public JSONResult myFriends(String userId) {
        // 0. userId 判断不能为空
        if (StringUtils.isBlank(userId)) {
            return JSONResult.errorMsg("");
        }

        // 1. 数据库查询好友列表
        List<MyFriendsVO> myFirends = userService.queryMyFriends(userId);

        return JSONResult.ok(myFirends);
    }

    /**
     *
     * @Description: 用户手机端获取未签收的消息列表
     */
    @PostMapping("/getUnReadMsgList")
    public JSONResult getUnReadMsgList(String acceptUserId) {
        // 0. userId 判断不能为空
        if (StringUtils.isBlank(acceptUserId)) {
            return JSONResult.errorMsg("");
        }

        // 查询列表
        List<com.cz.pojo.ChatMsg> unreadMsgList = userService.getUnReadMsgList(acceptUserId);

        return JSONResult.ok(unreadMsgList);
    }

}
