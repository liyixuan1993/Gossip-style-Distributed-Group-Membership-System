package edu.upenn.ds.team.mp2.service;

import io.netty.channel.ChannelHandler;

/**
 * Factory for generating a new {@link io.netty.channel.ChannelHandler} instance.
 */
public interface ChannelHandlerFactory {

  ChannelHandler newInstance();
}
