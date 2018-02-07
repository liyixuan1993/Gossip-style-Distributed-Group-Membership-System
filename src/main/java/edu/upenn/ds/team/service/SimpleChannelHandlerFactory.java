package edu.upenn.ds.team.mp2.service;

import io.netty.channel.ChannelHandler;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for generating a new {@link ChannelHandler} instance.
 */
public class SimpleChannelHandlerFactory implements ChannelHandlerFactory {

  private static final Logger LOG = Logger.getLogger(SimpleChannelInboundHandler.class.getName());
  private final Class<? extends ChannelHandler> channelHandlerClass;

  public SimpleChannelHandlerFactory(Class<? extends ChannelHandler> channelHandlerClass) {
    this.channelHandlerClass = channelHandlerClass;
  }

  public ChannelHandler newInstance() {
    try {
      return channelHandlerClass.cast(channelHandlerClass.newInstance());
    } catch (final InstantiationException | IllegalAccessException e) {
      LOG.log(Level.WARNING, "Failed to generate new channel handler instance", e);
      return null;
    }
  }
}
