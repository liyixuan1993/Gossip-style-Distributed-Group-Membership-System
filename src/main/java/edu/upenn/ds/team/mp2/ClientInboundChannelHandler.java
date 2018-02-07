package edu.upenn.ds.team.mp2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
//netty是一套在java NIO的基础上封装的便于用户开发网络应用程序的api
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client-side inbound channel handler
 */
public final class ClientInboundChannelHandler extends ChannelInboundHandlerAdapter {

  private final Logger LOG = Logger.getLogger(ClientInboundChannelHandler.class.getName());

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    LOG.log(Level.FINER, "Exception is throw in outbound channel {0}", cause);
    ctx.close();
  }
}
