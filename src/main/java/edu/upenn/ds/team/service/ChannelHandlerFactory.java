package edu.illinois.cs425.team4.service;

import io.netty.channel.ChannelHandler;

/**
 * Factory for generating a new {@link io.netty.channel.ChannelHandler} instance.
 */
public interface ChannelHandlerFactory {

  ChannelHandler newInstance();
}
