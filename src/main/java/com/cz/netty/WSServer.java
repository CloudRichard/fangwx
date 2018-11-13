package com.cz.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class WSServer {



    private static class SingletionWSServer{
        static final WSServer instance=new WSServer();
    }

    public static WSServer getInstance(){
        return SingletionWSServer.instance;
    }

    private static EventLoopGroup mainGroup;
    private static EventLoopGroup subGroup;
    private static ServerBootstrap sever;
    private static ChannelFuture future;

    public WSServer(){
        mainGroup = new NioEventLoopGroup();
        subGroup = new NioEventLoopGroup();
        sever = new ServerBootstrap();
        sever.group(mainGroup, subGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new WSServerInitiializer());
    }

    public void start(){
        this.future=sever.bind(8088);
        System.err.println("netty server启动完毕");
    }

}
